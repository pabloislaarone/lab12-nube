package pe.edu.tecsup.marcador.api.ranking;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pe.edu.tecsup.marcador.api.config.AppProperties;
import pe.edu.tecsup.marcador.api.realtime.RankingStream;
import pe.edu.tecsup.marcador.api.security.AuthUser;
import pe.edu.tecsup.marcador.scoring.ScoringConfig;
import pe.edu.tecsup.marcador.scoring.ScoringEngine;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RankingController {

    private final RankingService ranking;
    private final StatsService stats;
    private final RankingStream stream;
    private final ScoringEngine engine;
    private final AppProperties props;
    private final Instant startedAt = Instant.now();

    public RankingController(RankingService ranking, StatsService stats, RankingStream stream,
                             ScoringEngine engine, AppProperties props) {
        this.ranking = ranking;
        this.stats = stats;
        this.stream = stream;
        this.engine = engine;
        this.props = props;
    }

    @GetMapping("/ranking")
    public List<RankingRow> global() {
        return ranking.global();
    }

    @GetMapping("/stats/me")
    public StatsService.MyStats myStats(@AuthenticationPrincipal AuthUser user) {
        return stats.forUser(user.id());
    }

    /** Eventos en tiempo real: avisa cuando el ranking cambia. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return stream.subscribe();
    }

    /** Reglas de puntuación vigentes (las lee el frontend para mostrarlas). */
    @GetMapping("/rules")
    public Map<String, Object> rules() {
        ScoringConfig c = engine.config();
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("exactPoints", c.exactPoints());
        rules.put("winnerPoints", c.winnerPoints());
        rules.put("goalDiffPoints", c.goalDiffPoints());
        rules.put("streakLength", c.streakLength());
        rules.put("streakBonus", c.streakBonus());
        rules.put("earlyBonus", c.earlyBonus());
        rules.put("earlyThresholdHours", c.earlyThreshold().toHours());
        rules.put("lastMinuteWindowMinutes", c.lastMinuteWindow().toMinutes());
        rules.put("combineMode", c.combineMode().name());
        rules.put("earlyBonusRequiresHit", c.earlyBonusRequiresHit());
        return rules;
    }

    /** Qué réplica respondió: sirve para evidenciar el balanceo de carga. */
    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("service", "marcador-api");
        info.put("instance", props.instanceId());
        info.put("startedAt", startedAt);
        info.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        info.put("sseClients", stream.clients());
        return info;
    }
}
