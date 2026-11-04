package pe.edu.tecsup.marcador.scoring;

/**
 * Desglose de puntos de un participante en un partido.
 *
 * @param rule        regla base aplicada
 * @param basePoints  puntos de las reglas 1-3
 * @param winnerHit   acertó ganador/empate
 * @param goalDiffHit acertó diferencia de goles
 * @param early       registró con más de 24 h de anticipación
 * @param lastMinute  registró en los últimos 10 minutos (solo puntos base)
 * @param earlyBonus  puntos de la regla 5
 * @param streakBonus puntos de la regla 4
 * @param streakAfter racha acumulada después de este partido
 * @param total       basePoints + earlyBonus + streakBonus
 */
public record MatchScore(
        long matchId,
        BaseRule rule,
        int basePoints,
        boolean winnerHit,
        boolean goalDiffHit,
        boolean early,
        boolean lastMinute,
        int earlyBonus,
        int streakBonus,
        int streakAfter,
        int total) {
}
