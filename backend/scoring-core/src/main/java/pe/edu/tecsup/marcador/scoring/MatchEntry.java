package pe.edu.tecsup.marcador.scoring;

import java.time.Instant;
import java.util.Objects;

/**
 * Un partido FINALIZADO junto con el pronóstico del participante (puede ser null si no pronosticó).
 */
public record MatchEntry(long matchId, Instant kickoff, Score result, Prediction prediction) {

    public MatchEntry {
        Objects.requireNonNull(kickoff, "kickoff");
        Objects.requireNonNull(result, "result");
    }

    public boolean predicted() {
        return prediction != null;
    }
}
