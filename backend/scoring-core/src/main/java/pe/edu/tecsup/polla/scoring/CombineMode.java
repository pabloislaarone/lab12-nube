package pe.edu.tecsup.polla.scoring;

/**
 * Cómo se combinan las reglas base 1, 2 y 3.
 */
public enum CombineMode {
    /** Solo vale la regla con más puntos (5, 3 o 2). Es la interpretación por defecto del laboratorio. */
    HIGHEST,
    /** El marcador exacto vale 5; si no, se suman ganador (3) + diferencia (2). */
    CUMULATIVE
}
