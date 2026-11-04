package pe.edu.tecsup.polla.api.match;

import java.time.Instant;

/**
 * Partido tal como lo ve un participante: incluye su pronóstico y sus puntos (si los hay).
 */
public record MatchView(
        long id,
        String stage,
        String homeTeam,
        String awayTeam,
        String homeCode,
        String awayCode,
        String venue,
        Instant kickoffAt,
        String status,
        Integer homeGoals,
        Integer awayGoals,
        boolean locked,
        long predictionsCount,
        MyPrediction myPrediction,
        MyScore myScore) {

    public record MyPrediction(int homeGoals, int awayGoals, Instant submittedAt, boolean early, boolean lastMinute) {
    }

    public record MyScore(String rule, int basePoints, int earlyBonus, int streakBonus, int total) {
    }
}
