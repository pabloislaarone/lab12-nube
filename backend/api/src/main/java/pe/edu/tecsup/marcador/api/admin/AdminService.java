package pe.edu.tecsup.marcador.api.admin;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pe.edu.tecsup.marcador.api.admin.AdminController.MatchRequest;
import pe.edu.tecsup.marcador.api.common.ApiException;
import pe.edu.tecsup.marcador.api.common.Times;
import pe.edu.tecsup.marcador.api.match.MatchRepository;
import pe.edu.tecsup.marcador.api.match.MatchView;

import java.time.Instant;

@Service
public class AdminService {

    private final JdbcClient jdbc;
    private final MatchRepository matches;
    private final ScoringPublisher publisher;

    public AdminService(JdbcClient jdbc, MatchRepository matches, ScoringPublisher publisher) {
        this.jdbc = jdbc;
        this.matches = matches;
        this.publisher = publisher;
    }

    public MatchView create(MatchRequest r) {
        long id = jdbc.sql("""
                        INSERT INTO matches (stage, home_team, away_team, home_code, away_code, venue, kickoff_at)
                        VALUES (:stage, :home, :away, :hcode, :acode, :venue, :kickoff)
                        RETURNING id
                        """)
                .param("stage", r.stage().trim())
                .param("home", r.homeTeam().trim())
                .param("away", r.awayTeam().trim())
                .param("hcode", r.homeCode().toUpperCase())
                .param("acode", r.awayCode().toUpperCase())
                .param("venue", r.venue())
                .param("kickoff", Times.odt(r.kickoffAt()))
                .query(Long.class)
                .single();
        return matches.findView(id, null).orElseThrow();
    }

    /** Solo se editan partidos que aún no tienen resultado. Cambiar la fecha permite simular escenarios. */
    public MatchView update(long id, MatchRequest r) {
        var match = matches.findInfo(id).orElseThrow(() -> ApiException.notFound("Partido no encontrado"));
        if (!"SCHEDULED".equals(match.status())) {
            throw ApiException.conflict("El partido ya tiene resultado; corrige el marcador en lugar de editarlo");
        }
        jdbc.sql("""
                        UPDATE matches SET stage = :stage, home_team = :home, away_team = :away,
                               home_code = :hcode, away_code = :acode, venue = :venue, kickoff_at = :kickoff
                        WHERE id = :id
                        """)
                .param("stage", r.stage().trim())
                .param("home", r.homeTeam().trim())
                .param("away", r.awayTeam().trim())
                .param("hcode", r.homeCode().toUpperCase())
                .param("acode", r.awayCode().toUpperCase())
                .param("venue", r.venue())
                .param("kickoff", Times.odt(r.kickoffAt()))
                .param("id", id)
                .update();
        return matches.findView(id, null).orElseThrow();
    }

    public void delete(long id) {
        var match = matches.findInfo(id).orElseThrow(() -> ApiException.notFound("Partido no encontrado"));
        jdbc.sql("DELETE FROM matches WHERE id = :id").param("id", id).update();
        if ("FINISHED".equals(match.status())) {
            publisher.publish(id, "MATCH_DELETED");
        }
    }

    /**
     * Registra (o corrige) el resultado y publica el evento para que el worker recalcule.
     * La API no calcula puntos: así una petición lenta nunca bloquea a los usuarios.
     */
    public MatchView setResult(long id, int homeGoals, int awayGoals) {
        var match = matches.findInfo(id).orElseThrow(() -> ApiException.notFound("Partido no encontrado"));
        if (Instant.now().isBefore(match.kickoffAt())) {
            throw ApiException.conflict("El partido aún no empieza: no se puede registrar el resultado");
        }
        boolean correction = "FINISHED".equals(match.status());
        jdbc.sql("""
                        UPDATE matches SET status = 'FINISHED', home_goals = :home, away_goals = :away,
                               finished_at = now()
                        WHERE id = :id
                        """)
                .param("home", homeGoals)
                .param("away", awayGoals)
                .param("id", id)
                .update();
        publisher.publish(id, correction ? "RESULT_CORRECTED" : "MATCH_FINISHED");
        return matches.findView(id, null).orElseThrow();
    }

    public void requestRecompute() {
        publisher.publish(null, "MANUAL");
    }
}
