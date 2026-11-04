package pe.edu.tecsup.marcador.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import pe.edu.tecsup.marcador.events.ScoringEvent;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de punta a punta de la API contra Postgres, Redis y RabbitMQ reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    AmqpAdmin amqpAdmin;

    String tokenAna;
    String tokenBeto;
    String tokenAdmin;
    long futureMatchId;
    long pastMatchId;
    long roomId;
    String inviteCode;

    @BeforeAll
    void setUp() throws Exception {
        tokenAna = register("Ana Torres", "ana@test.pe");
        tokenBeto = register("Beto Ruiz", "beto@test.pe");
        tokenAdmin = login("admin@marcador.pe", "admin123");

        JsonNode matches = json(mvc.perform(get("/api/matches")).andReturn().getResponse().getContentAsString());
        List<JsonNode> list = new ArrayList<>();
        matches.forEach(list::add);
        futureMatchId = list.stream().filter(m -> !m.get("locked").asBoolean()).findFirst().orElseThrow()
                .get("id").asLong();
        pastMatchId = list.stream().filter(m -> m.get("locked").asBoolean()).findFirst().orElseThrow()
                .get("id").asLong();
    }

    @Test
    @Order(1)
    void cadaRespuestaIndicaLaReplicaQueAtendio() throws Exception {
        mvc.perform(get("/api/info"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Instance-Id"))
                .andExpect(jsonPath("$.service").value("marcador-api"));
    }

    @Test
    @Order(2)
    void exponeLasReglasDelLaboratorio() throws Exception {
        mvc.perform(get("/api/rules"))
                .andExpect(jsonPath("$.exactPoints").value(5))
                .andExpect(jsonPath("$.winnerPoints").value(3))
                .andExpect(jsonPath("$.goalDiffPoints").value(2))
                .andExpect(jsonPath("$.streakBonus").value(2))
                .andExpect(jsonPath("$.earlyBonus").value(1));
    }

    @Test
    @Order(3)
    void loginConContrasenaIncorrectaDevuelve401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ana@test.pe\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(4)
    void correoDuplicadoDevuelve409() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Otra Ana\",\"email\":\"ana@test.pe\",\"password\":\"secreto1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(5)
    void registraPrediccionEnPartidoFuturo() throws Exception {
        mvc.perform(auth(put("/api/matches/" + futureMatchId + "/prediction"), tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":2,\"awayGoals\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myPrediction.homeGoals").value(2))
                .andExpect(jsonPath("$.myPrediction.awayGoals").value(1));

        // editar la predicción mantiene un solo registro
        mvc.perform(auth(put("/api/matches/" + futureMatchId + "/prediction"), tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":0,\"awayGoals\":0}"))
                .andExpect(jsonPath("$.myPrediction.homeGoals").value(0))
                .andExpect(jsonPath("$.predictionsCount").value(1));
    }

    @Test
    @Order(6)
    void noSePuedePronosticarUnPartidoYaIniciado() throws Exception {
        mvc.perform(auth(put("/api/matches/" + pastMatchId + "/prediction"), tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":1,\"awayGoals\":0}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(7)
    void pronosticoSinSesionDevuelve401() throws Exception {
        mvc.perform(put("/api/matches/" + futureMatchId + "/prediction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":1,\"awayGoals\":0}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(8)
    void creaSalaYOtroUsuarioSeUneConElCodigo() throws Exception {
        String body = mvc.perform(auth(post("/api/rooms"), tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Amigos TECSUP\",\"description\":\"Marcador 26 del salón\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andReturn().getResponse().getContentAsString();
        JsonNode room = json(body);
        roomId = room.get("id").asLong();
        inviteCode = room.get("inviteCode").asText();
        assertThat(inviteCode).hasSize(8);

        mvc.perform(auth(get("/api/rooms/invite/" + inviteCode), tokenBeto))
                .andExpect(jsonPath("$.name").value("Amigos TECSUP"))
                .andExpect(jsonPath("$.alreadyMember").value(false));

        mvc.perform(auth(post("/api/rooms/join"), tokenBeto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + inviteCode.toLowerCase() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membersCount").value(2))
                .andExpect(jsonPath("$.myRole").value("MEMBER"));

        mvc.perform(auth(get("/api/rooms/" + roomId + "/ranking"), tokenBeto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @Order(9)
    void soloElDuenoAdministraLaSala() throws Exception {
        mvc.perform(auth(post("/api/rooms/" + roomId + "/invite-code"), tokenBeto))
                .andExpect(status().isForbidden());
        mvc.perform(auth(post("/api/rooms/" + roomId + "/invite-code"), tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value(org.hamcrest.Matchers.not(inviteCode)));
        mvc.perform(auth(post("/api/rooms/" + roomId + "/leave"), tokenAna))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(10)
    void unNoMiembroNoVeLaSala() throws Exception {
        String intruso = register("Intruso", "intruso@test.pe");
        mvc.perform(auth(get("/api/rooms/" + roomId), intruso))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    void miembroAbandonaLaSala() throws Exception {
        mvc.perform(auth(post("/api/rooms/" + roomId + "/leave"), tokenBeto))
                .andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/rooms/" + roomId), tokenAna))
                .andExpect(jsonPath("$.members.length()").value(1));
    }

    @Test
    @Order(12)
    void soloElAdminCargaResultados() throws Exception {
        mvc.perform(auth(put("/api/admin/matches/" + pastMatchId + "/result"), tokenAna)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":2,\"awayGoals\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(13)
    void cargarResultadoPublicaEventoEnRabbit() throws Exception {
        mvc.perform(auth(put("/api/admin/matches/" + pastMatchId + "/result"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":2,\"awayGoals\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"))
                .andExpect(jsonPath("$.homeGoals").value(2));

        var info = amqpAdmin.getQueueInfo(ScoringEvent.QUEUE);
        assertThat(info).isNotNull();
        assertThat(info.getMessageCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(14)
    void noSeCargaResultadoDeUnPartidoFuturo() throws Exception {
        mvc.perform(auth(put("/api/admin/matches/" + futureMatchId + "/result"), tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"homeGoals\":1,\"awayGoals\":1}"))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(15)
    void elRankingGlobalListaParticipantesSinAdmin() throws Exception {
        String body = mvc.perform(auth(get("/api/ranking"), tokenAna))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode rows = json(body);
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);
        rows.forEach(r -> assertThat(r.get("name").asText()).isNotEqualTo("Administrador"));
    }

    @Test
    @Order(16)
    void estadisticasConIaRespondenConAnalisis() throws Exception {
        mvc.perform(auth(get("/api/stats/me/insights"), tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.tip").isNotEmpty())
                .andExpect(jsonPath("$.ai").value(false)); // en pruebas no hay clave de IA
        mvc.perform(auth(get("/api/stats/me/insights"), tokenAdmin))
                .andExpect(status().isBadRequest());
    }

    // ---------- utilidades ----------

    private String register(String name, String email) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"secreto1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json(body).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json(body).get("token").asText();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }
}
