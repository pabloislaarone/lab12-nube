package pe.edu.tecsup.polla.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pe.edu.tecsup.polla.events.ScoringEvent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración del worker: usa el mismo esquema de la API (migraciones Flyway)
 * y verifica el cálculo de puntos, la idempotencia y el consumo desde RabbitMQ.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.flyway.locations=filesystem:../api/src/main/resources/db/migration"
})
class ScoringWorkerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"));

    @Autowired
    JdbcClient jdbc;
    @Autowired
    ScoringService scoring;
    @Autowired
    RabbitTemplate rabbitTemplate;

    long ana;
    long beto;
    List<Long> matchIds;

    @BeforeEach
    void setUp() {
        jdbc.sql("DELETE FROM predictions").update();
        jdbc.sql("DELETE FROM prediction_scores").update();
        jdbc.sql("DELETE FROM user_scores").update();
        jdbc.sql("DELETE FROM users").update();
        jdbc.sql("UPDATE matches SET status = 'SCHEDULED', home_goals = NULL, away_goals = NULL").update();

        ana = user("Ana", "ana@test.pe");
        beto = user("Beto", "beto@test.pe");

        matchIds = jdbc.sql("SELECT id FROM matches WHERE kickoff_at < now() ORDER BY kickoff_at LIMIT 3")
                .query(Long.class).list();
        finish(matchIds.get(0), 1, 0);
        finish(matchIds.get(1), 2, 2);
        finish(matchIds.get(2), 3, 1);

        // Ana: exacto anticipado (5+1), empate (3), exacto con racha (5+2) = 16
        predict(ana, matchIds.get(0), 1, 0, 48 * 60);
        predict(ana, matchIds.get(1), 1, 1, 120);
        predict(ana, matchIds.get(2), 3, 1, 120);
        // Beto: solo el primero, de último minuto (solo base: 3); no pronostica los demás = 3
        predict(beto, matchIds.get(0), 2, 0, 5);
    }

    @Test
    void calculaLosPuntosSegunLasCincoReglas() {
        scoring.recomputeAll(matchIds.get(2), "TEST");

        assertThat(total(ana)).isEqualTo(16);
        assertThat(total(beto)).isEqualTo(3);

        Integer streakBonus = jdbc.sql("SELECT streak_bonus FROM prediction_scores WHERE user_id = :u AND match_id = :m")
                .param("u", ana).param("m", matchIds.get(2)).query(Integer.class).single();
        assertThat(streakBonus).isEqualTo(2);

        Boolean lastMinute = jdbc.sql("SELECT last_minute FROM prediction_scores WHERE user_id = :u AND match_id = :m")
                .param("u", beto).param("m", matchIds.get(0)).query(Boolean.class).single();
        assertThat(lastMinute).isTrue();
    }

    @Test
    void recalcularDosVecesDaElMismoResultado() {
        scoring.recomputeAll(null, "TEST");
        scoring.recomputeAll(null, "TEST");

        assertThat(total(ana)).isEqualTo(16);
        int rows = jdbc.sql("SELECT count(*) FROM prediction_scores").query(Integer.class).single();
        assertThat(rows).isEqualTo(2 * 3); // 2 usuarios x 3 partidos finalizados, sin duplicados
    }

    @Test
    void consumeElEventoDesdeRabbitMQ() throws InterruptedException {
        int before = runs();
        rabbitTemplate.convertAndSend(ScoringEvent.EXCHANGE, ScoringEvent.ROUTING_KEY,
                new ScoringEvent(matchIds.get(0), "MATCH_FINISHED", System.currentTimeMillis()));

        long deadline = System.currentTimeMillis() + 15_000;
        while (runs() == before && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertThat(runs()).isGreaterThan(before);
        assertThat(total(ana)).isEqualTo(16);
    }

    // ---------- utilidades ----------

    private long user(String name, String email) {
        return jdbc.sql("INSERT INTO users (name, email, password_hash) VALUES (:n, :e, 'x') RETURNING id")
                .param("n", name).param("e", email).query(Long.class).single();
    }

    private void finish(long matchId, int home, int away) {
        jdbc.sql("UPDATE matches SET status = 'FINISHED', home_goals = :h, away_goals = :a WHERE id = :id")
                .param("h", home).param("a", away).param("id", matchId).update();
    }

    private void predict(long userId, long matchId, int home, int away, int minutesBefore) {
        jdbc.sql("""
                        INSERT INTO predictions (user_id, match_id, home_goals, away_goals, submitted_at)
                        SELECT :u, id, :h, :a, kickoff_at - make_interval(mins => :mins) FROM matches WHERE id = :m
                        """)
                .param("u", userId).param("m", matchId).param("h", home).param("a", away)
                .param("mins", minutesBefore)
                .update();
    }

    private int total(long userId) {
        return jdbc.sql("SELECT total_points FROM user_scores WHERE user_id = :u")
                .param("u", userId).query(Integer.class).single();
    }

    private int runs() {
        return jdbc.sql("SELECT count(*) FROM scoring_runs").query(Integer.class).single();
    }
}
