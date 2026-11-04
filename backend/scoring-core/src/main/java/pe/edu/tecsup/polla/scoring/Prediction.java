package pe.edu.tecsup.polla.scoring;

import java.time.Instant;
import java.util.Objects;

/**
 * Pronóstico de un participante. {@code submittedAt} es la última vez que se registró o editó.
 */
public record Prediction(Score score, Instant submittedAt) {

    public Prediction {
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }
}
