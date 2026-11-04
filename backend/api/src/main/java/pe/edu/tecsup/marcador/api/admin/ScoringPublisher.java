package pe.edu.tecsup.marcador.api.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import pe.edu.tecsup.marcador.events.ScoringEvent;

/** Publica en RabbitMQ el evento que dispara el recálculo de puntajes en el worker. */
@Component
public class ScoringPublisher {

    private static final Logger log = LoggerFactory.getLogger(ScoringPublisher.class);

    private final RabbitTemplate rabbit;

    public ScoringPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publish(Long matchId, String reason) {
        ScoringEvent event = new ScoringEvent(matchId, reason, System.currentTimeMillis());
        rabbit.convertAndSend(ScoringEvent.EXCHANGE, ScoringEvent.ROUTING_KEY, event);
        log.info("Evento de puntuación publicado: {}", event);
    }
}
