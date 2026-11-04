package pe.edu.tecsup.marcador.api.ranking;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ranking global y por sala. Se guarda en Redis (caché compartida por todas las réplicas);
 * el worker borra estas claves cada vez que recalcula los puntajes.
 */
@Service
public class RankingService {

    public static final String GLOBAL_KEY = "lb:global";
    public static final String ROOM_KEY_PREFIX = "lb:room:";
    private static final Duration TTL = Duration.ofSeconds(60);
    private static final TypeReference<List<RankingRow>> ROWS = new TypeReference<>() {
    };

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    private static final String COLUMNS = """
            u.id, u.name,
            COALESCE(s.total_points, 0)    AS total_points,
            COALESCE(s.exact_hits, 0)      AS exact_hits,
            COALESCE(s.winner_hits, 0)     AS winner_hits,
            COALESCE(s.current_streak, 0)  AS current_streak,
            COALESCE(s.best_streak, 0)     AS best_streak,
            COALESCE(s.predicted_count, 0) AS predicted_count
            """;
    private static final String ORDER = " ORDER BY total_points DESC, exact_hits DESC, winner_hits DESC, u.name";

    private final JdbcClient jdbc;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public RankingService(JdbcClient jdbc, StringRedisTemplate redis, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.redis = redis;
        this.mapper = mapper;
    }

    public List<RankingRow> global() {
        return cached(GLOBAL_KEY, () -> rank(jdbc.sql("SELECT " + COLUMNS + """
                        FROM users u LEFT JOIN user_scores s ON s.user_id = u.id
                        WHERE u.role = 'USER'
                        """ + ORDER + " LIMIT 500")));
    }

    public List<RankingRow> room(long roomId) {
        return cached(ROOM_KEY_PREFIX + roomId, () -> rank(jdbc.sql("SELECT " + COLUMNS + """
                        FROM room_members rm
                        JOIN users u ON u.id = rm.user_id
                        LEFT JOIN user_scores s ON s.user_id = u.id
                        WHERE rm.room_id = :rid
                        """ + ORDER).param("rid", roomId)));
    }

    public void evictRoom(long roomId) {
        try {
            redis.delete(ROOM_KEY_PREFIX + roomId);
        } catch (RuntimeException ex) {
            log.warn("No se pudo limpiar la caché de la sala {}: {}", roomId, ex.getMessage());
        }
    }

    /** Lee de Redis; si no está (o Redis falla) consulta la BD y guarda el resultado. */
    private List<RankingRow> cached(String key, Supplier<List<RankingRow>> loader) {
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return mapper.readValue(json, ROWS);
            }
        } catch (Exception ex) {
            log.warn("Caché de ranking no disponible ({}), se consulta la BD", ex.getMessage());
        }
        List<RankingRow> rows = loader.get();
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(rows), TTL);
        } catch (Exception ex) {
            log.warn("No se pudo guardar el ranking en caché: {}", ex.getMessage());
        }
        return rows;
    }

    /** Asigna posiciones con empates (1, 1, 3...) cuando coinciden puntos y exactos. */
    private List<RankingRow> rank(JdbcClient.StatementSpec spec) {
        List<RankingRow> raw = spec.query((rs, i) -> new RankingRow(0, rs.getLong("id"), rs.getString("name"),
                rs.getInt("total_points"), rs.getInt("exact_hits"), rs.getInt("winner_hits"),
                rs.getInt("current_streak"), rs.getInt("best_streak"), rs.getInt("predicted_count"))).list();
        List<RankingRow> ranked = new ArrayList<>(raw.size());
        int position = 0;
        for (int i = 0; i < raw.size(); i++) {
            RankingRow r = raw.get(i);
            boolean tie = i > 0
                    && raw.get(i - 1).totalPoints() == r.totalPoints()
                    && raw.get(i - 1).exactHits() == r.exactHits();
            if (!tie) {
                position = i + 1;
            }
            ranked.add(new RankingRow(position, r.userId(), r.name(), r.totalPoints(), r.exactHits(),
                    r.winnerHits(), r.currentStreak(), r.bestStreak(), r.predictedCount()));
        }
        return ranked;
    }
}
