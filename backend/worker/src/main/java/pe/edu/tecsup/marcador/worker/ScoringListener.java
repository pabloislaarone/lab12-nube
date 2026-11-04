package pe.edu.tecsup.marcador.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import pe.edu.tecsup.marcador.events.ScoringEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Consume los eventos de RabbitMQ. Con varias réplicas, RabbitMQ reparte los mensajes
 * entre ellas (competing consumers). Al terminar, limpia la caché del ranking y avisa
 * por Redis pub/sub a las réplicas de la API para que notifiquen a los navegadores.
 */
@Component
public class ScoringListener {

    public static final String RANKING_CHANNEL = "marcador:ranking-updated";
    private static final Logger log = LoggerFactory.getLogger(ScoringListener.class);

    private final ScoringService scoring;
    private final StringRedisTemplate redis;

    public ScoringListener(ScoringService scoring, StringRedisTemplate redis) {
        this.scoring = scoring;
        this.redis = redis;
    }

    @RabbitListener(queues = ScoringEvent.QUEUE)
    public void onEvent(ScoringEvent event) {
        log.info("Evento recibido: {}", event);
        ScoringService.RunResult result = scoring.recomputeAll(event.matchId(), event.reason());
        log.info("Puntajes recalculados: {} usuarios, {} partidos en {} ms",
                result.users(), result.matches(), result.durationMs());

        try {
            evictRankingCache();
            redis.convertAndSend(RANKING_CHANNEL,
                    "{\"matchId\":" + event.matchId() + ",\"reason\":\"" + event.reason() + "\"}");
        } catch (RuntimeException ex) {
            // Los puntajes ya están guardados; si Redis falla la caché expira sola en 60 s
            log.warn("No se pudo notificar por Redis: {}", ex.getMessage());
        }
    }

    private void evictRankingCache() {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor = redis.scan(ScanOptions.scanOptions().match("lb:*").count(200).build())) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
