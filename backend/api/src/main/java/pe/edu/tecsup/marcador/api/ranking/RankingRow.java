package pe.edu.tecsup.marcador.api.ranking;

public record RankingRow(
        int position,
        long userId,
        String name,
        int totalPoints,
        int exactHits,
        int winnerHits,
        int currentStreak,
        int bestStreak,
        int predictedCount) {
}
