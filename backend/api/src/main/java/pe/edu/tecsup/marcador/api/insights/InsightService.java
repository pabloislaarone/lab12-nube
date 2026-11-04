package pe.edu.tecsup.marcador.api.insights;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pe.edu.tecsup.marcador.api.common.Times;
import pe.edu.tecsup.marcador.api.insights.InsightEngine.Features;
import pe.edu.tecsup.marcador.api.insights.InsightEngine.Insights;
import pe.edu.tecsup.marcador.api.insights.InsightEngine.Row;
import pe.edu.tecsup.marcador.api.ranking.StatsService;

import java.time.Duration;
import java.util.List;

/**
 * Estadísticas con IA. Calcula los indicadores del participante, le pide el análisis al modelo
 * y guarda la respuesta en Redis: mientras los indicadores no cambien (no hay resultados nuevos)
 * no se vuelve a llamar a la IA, aunque la petición llegue a otra réplica.
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final Duration TTL = Duration.ofHours(6);

    private final JdbcClient jdbc;
    private final StatsService stats;
    private final AiClient ai;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public InsightService(JdbcClient jdbc, StatsService stats, AiClient ai, StringRedisTemplate redis,
                          ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.stats = stats;
        this.ai = ai;
        this.redis = redis;
        this.mapper = mapper;
    }

    public Insights forUser(long userId) {
        Features features = features(userId);
        if (features.predicted() == 0 || !ai.enabled()) {
            return InsightEngine.ruleBased(features);
        }

        String key = "insights:" + userId + ":" + Integer.toHexString(features.hashCode());
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return mapper.readValue(cached, Insights.class);
            }
        } catch (Exception ex) {
            log.warn("Caché de análisis no disponible: {}", ex.getMessage());
        }

        Insights result = ai.complete(InsightEngine.SYSTEM_PROMPT, InsightEngine.userPrompt(features, mapper))
                .flatMap(raw -> InsightEngine.parseAi(raw, ai.label(), mapper))
                .orElseGet(() -> InsightEngine.ruleBased(features));

        if (result.ai()) {
            try {
                redis.opsForValue().set(key, mapper.writeValueAsString(result), TTL);
            } catch (Exception ex) {
                log.warn("No se pudo guardar el análisis en caché: {}", ex.getMessage());
            }
        }
        return result;
    }

    Features features(long userId) {
        StatsService.MyStats s = stats.forUser(userId);
        List<Row> rows = jdbc.sql("""
                        SELECT s.rule, s.winner_hit, s.early, s.last_minute,
                               p.home_goals AS ph, p.away_goals AS pa, m.home_goals AS rh, m.away_goals AS ra
                        FROM prediction_scores s
                        JOIN matches m ON m.id = s.match_id
                        LEFT JOIN predictions p ON p.user_id = s.user_id AND p.match_id = s.match_id
                        WHERE s.user_id = :uid AND m.status = 'FINISHED'
                        ORDER BY m.kickoff_at
                        """)
                .param("uid", userId)
                .query((rs, i) -> new Row(rs.getString("rule"), rs.getBoolean("winner_hit"), rs.getBoolean("early"),
                        rs.getBoolean("last_minute"), Times.nullableInt(rs, "ph"), Times.nullableInt(rs, "pa"),
                        rs.getInt("rh"), rs.getInt("ra")))
                .list();
        return InsightEngine.features(rows, s.totalPoints(), s.position(), s.participants(), s.currentStreak(),
                s.bestStreak(), s.earlyPoints(), s.streakPoints());
    }
}
