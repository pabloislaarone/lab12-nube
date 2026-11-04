package pe.edu.tecsup.marcador.worker;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.tecsup.marcador.scoring.MatchEntry;
import pe.edu.tecsup.marcador.scoring.MatchScore;
import pe.edu.tecsup.marcador.scoring.Prediction;
import pe.edu.tecsup.marcador.scoring.Score;
import pe.edu.tecsup.marcador.scoring.ScoreSummary;
import pe.edu.tecsup.marcador.scoring.ScoringEngine;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Recalcula TODOS los puntajes desde cero a partir de los partidos finalizados.
 *
 * <p>Es idempotente: procesar el mismo evento dos veces deja exactamente el mismo resultado.
 * Un advisory lock de PostgreSQL garantiza que, aunque haya varias réplicas del worker,
 * solo una recalcula a la vez (las demás esperan su turno).</p>
 */
@Service
public class ScoringService {

    /** Identificador arbitrario del lock compartido por todas las réplicas. */
    private static final long LOCK_ID = 20260611L;

    public record RunResult(int users, int matches, long durationMs) {
    }

    private record FinishedMatch(long id, Instant kickoff, Score result) {
    }

    private record PredictionRow(long userId, long matchId, Prediction prediction) {
    }

    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final ScoringEngine engine;
    private final String instanceId;

    public ScoringService(JdbcClient jdbc, JdbcTemplate template, ScoringEngine engine, WorkerProperties props) {
        this.jdbc = jdbc;
        this.template = template;
        this.engine = engine;
        this.instanceId = props.instanceId();
    }

    @Transactional
    public RunResult recomputeAll(Long triggerMatch, String reason) {
        long start = System.nanoTime();

        // Exclusión mutua entre réplicas: se libera solo al terminar la transacción
        jdbc.sql("SELECT pg_advisory_xact_lock(:id)").param("id", LOCK_ID).query((rs, i) -> 1).list();

        List<FinishedMatch> matches = jdbc.sql("""
                        SELECT id, kickoff_at, home_goals, away_goals FROM matches
                        WHERE status = 'FINISHED' ORDER BY kickoff_at, id
                        """)
                .query((rs, i) -> new FinishedMatch(rs.getLong("id"),
                        rs.getObject("kickoff_at", OffsetDateTime.class).toInstant(),
                        new Score(rs.getInt("home_goals"), rs.getInt("away_goals"))))
                .list();

        List<Long> userIds = jdbc.sql("SELECT id FROM users WHERE role = 'USER' ORDER BY id")
                .query(Long.class).list();

        Map<Long, Map<Long, Prediction>> byUser = new HashMap<>();
        jdbc.sql("""
                        SELECT p.user_id, p.match_id, p.home_goals, p.away_goals, p.submitted_at
                        FROM predictions p JOIN matches m ON m.id = p.match_id
                        WHERE m.status = 'FINISHED'
                        """)
                .query((rs, i) -> new PredictionRow(rs.getLong("user_id"), rs.getLong("match_id"),
                        new Prediction(new Score(rs.getInt("home_goals"), rs.getInt("away_goals")),
                                rs.getObject("submitted_at", OffsetDateTime.class).toInstant())))
                .list()
                .forEach(p -> byUser.computeIfAbsent(p.userId(), k -> new HashMap<>()).put(p.matchId(), p.prediction()));

        List<Object[]> scoreRows = new ArrayList<>();
        List<Object[]> totalRows = new ArrayList<>();
        for (Long userId : userIds) {
            Map<Long, Prediction> predictions = byUser.getOrDefault(userId, Map.of());
            List<MatchEntry> entries = new ArrayList<>(matches.size());
            for (FinishedMatch m : matches) {
                entries.add(new MatchEntry(m.id(), m.kickoff(), m.result(), predictions.get(m.id())));
            }
            ScoreSummary summary = engine.score(entries);
            for (MatchScore s : summary.matches()) {
                scoreRows.add(new Object[]{userId, s.matchId(), s.rule().name(), s.basePoints(), s.winnerHit(),
                        s.goalDiffHit(), s.early(), s.lastMinute(), s.earlyBonus(), s.streakBonus(),
                        s.streakAfter(), s.total()});
            }
            totalRows.add(new Object[]{userId, summary.totalPoints(), summary.predictedCount(), summary.exactHits(),
                    summary.winnerHits(), summary.currentStreak(), summary.bestStreak(),
                    summary.earlyBonusPoints(), summary.streakBonusPoints()});
        }

        // Reemplazo completo: siempre queda el estado correcto, sin duplicados
        template.update("DELETE FROM prediction_scores");
        template.batchUpdate("""
                INSERT INTO prediction_scores (user_id, match_id, rule, base_points, winner_hit, goal_diff_hit,
                                               early, last_minute, early_bonus, streak_bonus, streak_after, total)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, scoreRows);
        template.batchUpdate("""
                INSERT INTO user_scores (user_id, total_points, predicted_count, exact_hits, winner_hits,
                                         current_streak, best_streak, early_points, streak_points, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET
                    total_points = EXCLUDED.total_points,
                    predicted_count = EXCLUDED.predicted_count,
                    exact_hits = EXCLUDED.exact_hits,
                    winner_hits = EXCLUDED.winner_hits,
                    current_streak = EXCLUDED.current_streak,
                    best_streak = EXCLUDED.best_streak,
                    early_points = EXCLUDED.early_points,
                    streak_points = EXCLUDED.streak_points,
                    updated_at = now()
                """, totalRows);

        long durationMs = (System.nanoTime() - start) / 1_000_000;
        jdbc.sql("""
                        INSERT INTO scoring_runs (trigger_match, reason, instance, users_processed,
                                                  matches_processed, duration_ms)
                        VALUES (:match, :reason, :instance, :users, :matches, :duration)
                        """)
                .param("match", triggerMatch)
                .param("reason", reason == null ? "UNKNOWN" : reason)
                .param("instance", instanceId)
                .param("users", userIds.size())
                .param("matches", matches.size())
                .param("duration", durationMs)
                .update();

        return new RunResult(userIds.size(), matches.size(), durationMs);
    }
}
