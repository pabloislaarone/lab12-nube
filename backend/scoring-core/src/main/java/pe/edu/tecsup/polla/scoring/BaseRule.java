package pe.edu.tecsup.polla.scoring;

/**
 * Regla base que se aplicó a un pronóstico.
 */
public enum BaseRule {
    /** Regla 1: marcador exacto. */
    EXACT,
    /** Regla 2: ganador (o empate) correcto. */
    WINNER,
    /** Regla 3: diferencia de goles correcta. */
    GOAL_DIFF,
    /** Pronosticó pero no acertó nada. */
    MISS,
    /** No registró pronóstico. */
    NOT_PREDICTED
}
