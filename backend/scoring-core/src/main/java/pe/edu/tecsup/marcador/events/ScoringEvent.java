package pe.edu.tecsup.marcador.events;

/**
 * Mensaje que la API publica en RabbitMQ y el worker consume para recalcular los puntajes.
 *
 * @param matchId     partido que lo originó (null si es un recálculo manual)
 * @param reason      MATCH_FINISHED, RESULT_CORRECTED, MANUAL, DEMO
 * @param requestedAt epoch millis en que se pidió
 */
public record ScoringEvent(Long matchId, String reason, long requestedAt) {

    public static final String EXCHANGE = "marcador.events";
    public static final String ROUTING_KEY = "scoring.recompute";
    public static final String QUEUE = "scoring.recompute.queue";
}
