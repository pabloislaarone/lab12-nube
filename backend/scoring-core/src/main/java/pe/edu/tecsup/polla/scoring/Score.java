package pe.edu.tecsup.polla.scoring;

/**
 * Marcador de un partido (real o pronosticado).
 */
public record Score(int home, int away) {

    public Score {
        if (home < 0 || away < 0) {
            throw new IllegalArgumentException("Los goles no pueden ser negativos");
        }
    }

    /** 1 si gana el local, -1 si gana el visitante, 0 si es empate. */
    public int outcome() {
        return Integer.signum(home - away);
    }

    /** Diferencia de goles con signo (local - visitante). */
    public int goalDifference() {
        return home - away;
    }

    @Override
    public String toString() {
        return home + "-" + away;
    }
}
