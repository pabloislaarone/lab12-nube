package pe.edu.tecsup.polla.api.match;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pe.edu.tecsup.polla.api.common.ApiException;
import pe.edu.tecsup.polla.api.security.AuthUser;

import java.time.Instant;

@Service
public class PredictionService {

    private final JdbcClient jdbc;
    private final MatchRepository matches;

    public PredictionService(JdbcClient jdbc, MatchRepository matches) {
        this.jdbc = jdbc;
        this.matches = matches;
    }

    /**
     * Registra o actualiza el pronóstico. La hora de registro (submitted_at) se toma del reloj de
     * la base de datos para que todas las réplicas usen la misma referencia.
     */
    public void save(AuthUser user, long matchId, int homeGoals, int awayGoals) {
        if (user.isAdmin()) {
            throw ApiException.forbidden("El administrador no participa en la polla");
        }
        var match = matches.findInfo(matchId)
                .orElseThrow(() -> ApiException.notFound("Partido no encontrado"));
        if (!"SCHEDULED".equals(match.status()) || !Instant.now().isBefore(match.kickoffAt())) {
            throw ApiException.conflict("Las predicciones para este partido ya están cerradas");
        }

        // La condición kickoff_at > now() vuelve a validar en la BD (evita carreras en el cierre)
        int rows = jdbc.sql("""
                        INSERT INTO predictions (user_id, match_id, home_goals, away_goals, submitted_at)
                        SELECT :uid, m.id, :home, :away, now()
                        FROM matches m
                        WHERE m.id = :mid AND m.status = 'SCHEDULED' AND m.kickoff_at > now()
                        ON CONFLICT (user_id, match_id) DO UPDATE
                           SET home_goals = EXCLUDED.home_goals,
                               away_goals = EXCLUDED.away_goals,
                               submitted_at = now()
                        """)
                .param("uid", user.id())
                .param("mid", matchId)
                .param("home", homeGoals)
                .param("away", awayGoals)
                .update();
        if (rows == 0) {
            throw ApiException.conflict("Las predicciones para este partido ya están cerradas");
        }
    }
}
