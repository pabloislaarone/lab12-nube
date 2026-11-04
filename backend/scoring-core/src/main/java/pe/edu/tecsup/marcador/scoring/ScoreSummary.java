package pe.edu.tecsup.marcador.scoring;

import java.util.List;

/**
 * Resultado de calcular todos los partidos finalizados de un participante.
 */
public record ScoreSummary(
        List<MatchScore> matches,
        int totalPoints,
        int predictedCount,
        int exactHits,
        int winnerHits,
        int currentStreak,
        int bestStreak,
        int earlyBonusPoints,
        int streakBonusPoints) {
}
