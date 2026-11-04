package pe.edu.tecsup.polla.scoring;

import java.time.Duration;
import java.util.Objects;

/**
 * Parámetros del sistema de puntuación. Todos los valores son configurables por variables de entorno.
 *
 * @param exactPoints           regla 1 (marcador exacto)
 * @param winnerPoints          regla 2 (ganador o empate)
 * @param goalDiffPoints        regla 3 (diferencia de goles)
 * @param streakLength          regla 4: cada cuántos aciertos seguidos se da el bonus
 * @param streakBonus           regla 4: puntos del bonus por racha
 * @param earlyBonus            regla 5: punto extra por predicción anticipada
 * @param earlyThreshold        regla 5: anticipación mínima para el punto extra (más de 24 h)
 * @param lastMinuteWindow      regla 5: ventana de "último minuto" (10 min) que solo recibe puntos base
 * @param combineMode           cómo se combinan las reglas 1, 2 y 3
 * @param earlyBonusRequiresHit si el punto por anticipación solo se da cuando el pronóstico sumó puntos base
 */
public record ScoringConfig(
        int exactPoints,
        int winnerPoints,
        int goalDiffPoints,
        int streakLength,
        int streakBonus,
        int earlyBonus,
        Duration earlyThreshold,
        Duration lastMinuteWindow,
        CombineMode combineMode,
        boolean earlyBonusRequiresHit) {

    public ScoringConfig {
        Objects.requireNonNull(earlyThreshold, "earlyThreshold");
        Objects.requireNonNull(lastMinuteWindow, "lastMinuteWindow");
        Objects.requireNonNull(combineMode, "combineMode");
        if (streakLength < 1) {
            throw new IllegalArgumentException("streakLength debe ser >= 1");
        }
    }

    /** Valores exactos del enunciado del laboratorio. */
    public static ScoringConfig defaults() {
        return new ScoringConfig(5, 3, 2, 3, 2, 1,
                Duration.ofHours(24), Duration.ofMinutes(10),
                CombineMode.HIGHEST, true);
    }

    public ScoringConfig withCombineMode(CombineMode mode) {
        return new ScoringConfig(exactPoints, winnerPoints, goalDiffPoints, streakLength, streakBonus,
                earlyBonus, earlyThreshold, lastMinuteWindow, mode, earlyBonusRequiresHit);
    }
}
