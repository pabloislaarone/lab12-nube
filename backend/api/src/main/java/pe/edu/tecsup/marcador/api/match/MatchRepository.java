package pe.edu.tecsup.marcador.api.match;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pe.edu.tecsup.marcador.api.common.Times;
import pe.edu.tecsup.marcador.scoring.MatchEntry;
import pe.edu.tecsup.marcador.scoring.Prediction;
import pe.edu.tecsup.marcador.scoring.Score;
import pe.edu.tecsup.marcador.scoring.ScoringEngine;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MatchRepository {

    /** Datos mínimos de un partido para validar reglas. */
    public record MatchInfo(long id, Instant kickoffAt, String status) {
    }

    private static final String SELECT_VIEW = """
            SELECT m.id, m.stage, m.home_team, m.away_team, m.home_code, m.away_code, m.venue,
                   m.kickoff_at, m.status, m.home_goals, m.away_goals,
                   p.home_goals AS p_home, p.away_goals AS p_away, p.submitted_at AS p_submitted,
                   s.rule AS s_rule, s.base_points AS s_base, s.early_bonus AS s_early,
                   s.streak_bonus AS s_streak, s.total AS s_total,
                   (SELECT count(*) FROM predictions px WHERE px.match_id = m.id) AS predictions_count
            FROM matches m
            LEFT JOIN predictions p ON p.match_id = m.id AND p.user_id = :uid
            LEFT JOIN prediction_scores s ON s.match_id = m.id AND s.user_id = :uid
            """;

    private final JdbcClient jdbc;
    private final ScoringEngine engine;

    public MatchRepository(JdbcClient jdbc, ScoringEngine engine) {
        this.jdbc = jdbc;
        this.engine = engine;
    }

    public List<MatchView> findAll(Long userId, String status) {
        String where = status == null ? "" : " WHERE m.status = :status";
        var spec = jdbc.sql(SELECT_VIEW + where + " ORDER BY m.kickoff_at, m.id")
                .param("uid", userId == null ? -1L : userId);
        if (status != null) {
            spec = spec.param("status", status);
        }
        return spec.query((rs, i) -> mapView(rs)).list();
    }

    public Optional<MatchView> findView(long matchId, Long userId) {
        return jdbc.sql(SELECT_VIEW + " WHERE m.id = :id")
                .param("uid", userId == null ? -1L : userId)
                .param("id", matchId)
                .query((rs, i) -> mapView(rs))
                .optional();
    }

    public Optional<MatchInfo> findInfo(long matchId) {
        return jdbc.sql("SELECT id, kickoff_at, status FROM matches WHERE id = :id")
                .param("id", matchId)
                .query((rs, i) -> new MatchInfo(rs.getLong("id"), Times.instant(rs, "kickoff_at"),
                        rs.getString("status")))
                .optional();
    }

    private MatchView mapView(ResultSet rs) throws SQLException {
        Instant kickoff = Times.instant(rs, "kickoff_at");
        String status = rs.getString("status");
        Integer homeGoals = Times.nullableInt(rs, "home_goals");
        Integer awayGoals = Times.nullableInt(rs, "away_goals");

        MatchView.MyPrediction myPrediction = null;
        Integer pHome = Times.nullableInt(rs, "p_home");
        if (pHome != null) {
            int pAway = rs.getInt("p_away");
            Instant submitted = Times.instant(rs, "p_submitted");
            Prediction prediction = new Prediction(new Score(pHome, pAway), submitted);
            // El resultado no importa para calcular anticipación: se usa un 0-0 de relleno
            MatchEntry entry = new MatchEntry(rs.getLong("id"), kickoff, new Score(0, 0), prediction);
            boolean lastMinute = engine.isLastMinute(prediction, entry);
            boolean early = !lastMinute && engine.isEarly(prediction, entry);
            myPrediction = new MatchView.MyPrediction(pHome, pAway, submitted, early, lastMinute);
        }

        MatchView.MyScore myScore = null;
        String rule = rs.getString("s_rule");
        if (rule != null) {
            myScore = new MatchView.MyScore(rule, rs.getInt("s_base"), rs.getInt("s_early"),
                    rs.getInt("s_streak"), rs.getInt("s_total"));
        }

        boolean locked = !"SCHEDULED".equals(status) || !Instant.now().isBefore(kickoff);

        return new MatchView(
                rs.getLong("id"),
                rs.getString("stage"),
                rs.getString("home_team"),
                rs.getString("away_team"),
                rs.getString("home_code"),
                rs.getString("away_code"),
                rs.getString("venue"),
                kickoff,
                status,
                homeGoals,
                awayGoals,
                locked,
                rs.getLong("predictions_count"),
                myPrediction,
                myScore);
    }
}
