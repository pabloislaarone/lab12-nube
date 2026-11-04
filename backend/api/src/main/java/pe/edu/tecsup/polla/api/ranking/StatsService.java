package pe.edu.tecsup.polla.api.ranking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Estadísticas personales del participante. */
@Service
public class StatsService {

    public record TimelinePoint(long matchId, String label, int points, int cumulative) {
    }

    public record MyStats(
            int totalPoints,
            int position,
            int participants,
            int finishedMatches,
            int predictedCount,
            int exactHits,
            int winnerHits,
            double accuracy,
            int currentStreak,
            int bestStreak,
            int earlyPoints,
            int streakPoints,
            Map<String, Integer> ruleCounts,
            List<TimelinePoint> timeline) {
    }

    private final JdbcClient jdbc;

    public StatsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public MyStats forUser(long userId) {
        record Totals(int total, int predicted, int exact, int winner, int current, int best, int early,
                      int streak) {
        }
        Totals t = jdbc.sql("""
                        SELECT total_points, predicted_count, exact_hits, winner_hits, current_streak,
                               best_streak, early_points, streak_points
                        FROM user_scores WHERE user_id = :uid
                        """)
                .param("uid", userId)
                .query((rs, i) -> new Totals(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                        rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8)))
                .optional()
                .orElse(new Totals(0, 0, 0, 0, 0, 0, 0, 0));

        int participants = jdbc.sql("SELECT count(*) FROM users WHERE role = 'USER'")
                .query(Integer.class).single();
        int better = jdbc.sql("""
                        SELECT count(*) FROM user_scores s JOIN users u ON u.id = s.user_id
                        WHERE u.role = 'USER' AND s.total_points > :total
                        """)
                .param("total", t.total())
                .query(Integer.class).single();
        int finished = jdbc.sql("SELECT count(*) FROM matches WHERE status = 'FINISHED'")
                .query(Integer.class).single();

        Map<String, Integer> ruleCounts = new LinkedHashMap<>();
        for (String rule : List.of("EXACT", "WINNER", "GOAL_DIFF", "MISS", "NOT_PREDICTED")) {
            ruleCounts.put(rule, 0);
        }
        jdbc.sql("SELECT rule, count(*) AS n FROM prediction_scores WHERE user_id = :uid GROUP BY rule")
                .param("uid", userId)
                .query((rs, i) -> Map.entry(rs.getString("rule"), rs.getInt("n")))
                .list()
                .forEach(e -> ruleCounts.put(e.getKey(), e.getValue()));

        List<TimelinePoint> timeline = new ArrayList<>();
        int cumulative = 0;
        var rows = jdbc.sql("""
                        SELECT m.id, m.home_code, m.away_code, s.total
                        FROM prediction_scores s JOIN matches m ON m.id = s.match_id
                        WHERE s.user_id = :uid
                        ORDER BY m.kickoff_at, m.id
                        """)
                .param("uid", userId)
                .query((rs, i) -> new TimelinePoint(rs.getLong("id"),
                        rs.getString("home_code") + "-" + rs.getString("away_code"), rs.getInt("total"), 0))
                .list();
        for (TimelinePoint p : rows) {
            cumulative += p.points();
            timeline.add(new TimelinePoint(p.matchId(), p.label(), p.points(), cumulative));
        }

        double accuracy = t.predicted() == 0 ? 0 : Math.round(1000.0 * t.winner() / t.predicted()) / 10.0;
        return new MyStats(t.total(), better + 1, participants, finished, t.predicted(), t.exact(), t.winner(),
                accuracy, t.current(), t.best(), t.early(), t.streak(), ruleCounts, timeline);
    }
}
