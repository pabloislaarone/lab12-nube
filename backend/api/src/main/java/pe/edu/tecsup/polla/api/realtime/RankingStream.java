package pe.edu.tecsup.polla.api.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pe.edu.tecsup.polla.api.config.AppProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Mantiene las conexiones SSE de los navegadores conectados a ESTA réplica.
 * El worker publica en Redis (pub/sub) y todas las réplicas reciben el aviso,
 * así cada una notifica a sus propios clientes. Funciona con N réplicas.
 */
@Component
public class RankingStream implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RankingStream.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final String instanceId;

    public RankingStream(AppProperties props) {
        this.instanceId = props.instanceId();
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data("{\"instance\":\"" + instanceId + "\"}"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public int clients() {
        return emitters.size();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("Ranking actualizado, notificando a {} clientes", emitters.size());
        broadcast(() -> SseEmitter.event().name("ranking").data(body));
    }

    /** Comentario cada 25 s para que el proxy no cierre la conexión por inactividad. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        broadcast(() -> SseEmitter.event().comment("ping"));
    }

    /** El builder de SSE no se puede reutilizar: se crea uno nuevo por cada cliente. */
    private void broadcast(Supplier<SseEmitter.SseEventBuilder> event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event.get());
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
