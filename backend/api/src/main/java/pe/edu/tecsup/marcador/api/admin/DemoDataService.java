package pe.edu.tecsup.marcador.api.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.edu.tecsup.marcador.api.common.Times;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Simulador para demostraciones y pruebas: crea participantes demo, sus pronósticos
 * (anticipados, normales, de último minuto y algunos faltantes) y carga resultados
 * para los partidos que ya empezaron. Los números aleatorios usan semilla fija,
 * así que la demo es reproducible.
 */
@Service
public class DemoDataService {

    public static final String DEMO_PASSWORD = "demo123";
    public static final String DEMO_ROOM_CODE = "DEMO2026";

    private static final String[] NAMES = {
            "Lucía Quispe", "Diego Mamani", "Valeria Flores", "Mateo Huamán", "Camila Rojas",
            "Sebastián Vargas", "Daniela Chávez", "Joaquín Ramos", "Fernanda Torres", "Gabriel Castillo",
            "Mariana Paredes", "Nicolás Salazar", "Ximena Gutiérrez", "Thiago Mendoza", "Antonella Cruz",
            "Adrián Espinoza", "Renata Medina", "Santiago Ríos", "Isabella Vega", "Emiliano Cárdenas"};

    private record MatchRow(long id, Instant kickoff) {
    }

    private final JdbcClient jdbc;
    private final JdbcTemplate template;
    private final PasswordEncoder encoder;
    private final ScoringPublisher publisher;
    private final TransactionTemplate tx;

    public DemoDataService(JdbcClient jdbc, JdbcTemplate template, PasswordEncoder encoder,
                           ScoringPublisher publisher, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.template = template;
        this.encoder = encoder;
        this.publisher = publisher;
        this.tx = new TransactionTemplate(txManager);
    }

    public Map<String, Object> seed(int userCount) {
        Map<String, Object> summary = tx.execute(status -> doSeed(userCount));
        // Se publica DESPUÉS del commit para que el worker vea los datos
        publisher.publish(null, "DEMO");
        return summary;
    }

    private Map<String, Object> doSeed(int userCount) {
        String hash = encoder.encode(DEMO_PASSWORD);
        List<Object[]> users = new ArrayList<>();
        List<String> emails = new ArrayList<>();
        for (int i = 1; i <= userCount; i++) {
            String name = NAMES[(i - 1) % NAMES.length] + (i > NAMES.length ? " " + ((i - 1) / NAMES.length + 1) : "");
            String email = String.format("demo%02d@marcador.pe", i);
            emails.add(email);
            users.add(new Object[]{name, email, hash});
        }
        template.batchUpdate("""
                INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)
                ON CONFLICT (email) DO NOTHING
                """, users);
        List<Long> userIds = jdbc.sql("SELECT id FROM users WHERE email IN (:emails) ORDER BY email")
                .param("emails", emails)
                .query(Long.class)
                .list();

        // 1) Partidos que ya empezaron y no tienen resultado: pronósticos con fecha pasada + resultado
        List<MatchRow> past = jdbc.sql("""
                        SELECT id, kickoff_at FROM matches
                        WHERE status = 'SCHEDULED' AND kickoff_at <= now()
                        ORDER BY kickoff_at
                        """)
                .query((rs, i) -> new MatchRow(rs.getLong("id"), Times.instant(rs, "kickoff_at")))
                .list();

        List<Object[]> predictions = new ArrayList<>();
        for (MatchRow match : past) {
            Random resultRandom = new Random(match.id() * 7919L);
            int home = goals(resultRandom);
            int away = goals(resultRandom);
            for (Long userId : userIds) {
                Random r = new Random(userId * 31L + match.id());
                if (r.nextDouble() < 0.08) {
                    continue; // no pronosticó: corta su racha
                }
                Duration advance = advance(r);
                int ph;
                int pa;
                if (r.nextDouble() < 0.22) {
                    ph = home;
                    pa = away;
                } else {
                    ph = goals(r);
                    pa = goals(r);
                }
                Instant submitted = match.kickoff().minus(advance);
                predictions.add(new Object[]{userId, match.id(), ph, pa, Times.odt(submitted), Times.odt(submitted)});
            }
            jdbc.sql("""
                            UPDATE matches SET status = 'FINISHED', home_goals = :h, away_goals = :a, finished_at = now()
                            WHERE id = :id
                            """)
                    .param("h", home).param("a", away).param("id", match.id())
                    .update();
        }
        template.batchUpdate("""
                INSERT INTO predictions (user_id, match_id, home_goals, away_goals, submitted_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, match_id) DO NOTHING
                """, predictions);

        // 2) Partidos futuros: algunos participantes ya pronosticaron
        List<Long> future = jdbc.sql("SELECT id FROM matches WHERE status = 'SCHEDULED' AND kickoff_at > now()")
                .query(Long.class).list();
        List<Object[]> upcoming = new ArrayList<>();
        for (Long matchId : future) {
            for (Long userId : userIds) {
                Random r = new Random(userId * 17L + matchId);
                if (r.nextDouble() < 0.6) {
                    upcoming.add(new Object[]{userId, matchId, goals(r), goals(r)});
                }
            }
        }
        template.batchUpdate("""
                INSERT INTO predictions (user_id, match_id, home_goals, away_goals)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, match_id) DO NOTHING
                """, upcoming);

        // 3) Sala de demostración con todos los participantes demo
        if (!userIds.isEmpty()) {
            jdbc.sql("""
                            INSERT INTO rooms (name, description, invite_code, owner_id)
                            VALUES ('Sala Demo TECSUP', 'Sala de prueba creada por el simulador', :code, :owner)
                            ON CONFLICT (invite_code) DO NOTHING
                            """)
                    .param("code", DEMO_ROOM_CODE)
                    .param("owner", userIds.get(0))
                    .update();
            long roomId = jdbc.sql("SELECT id FROM rooms WHERE invite_code = :code")
                    .param("code", DEMO_ROOM_CODE).query(Long.class).single();
            long ownerId = jdbc.sql("SELECT owner_id FROM rooms WHERE id = :id")
                    .param("id", roomId).query(Long.class).single();
            List<Object[]> members = new ArrayList<>();
            for (Long userId : userIds) {
                members.add(new Object[]{roomId, userId, userId == ownerId ? "OWNER" : "MEMBER"});
            }
            template.batchUpdate("""
                    INSERT INTO room_members (room_id, user_id, role) VALUES (?, ?, ?)
                    ON CONFLICT (room_id, user_id) DO NOTHING
                    """, members);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("users", userIds.size());
        summary.put("password", DEMO_PASSWORD);
        summary.put("finishedMatches", past.size());
        summary.put("pastPredictions", predictions.size());
        summary.put("upcomingPredictions", upcoming.size());
        summary.put("roomCode", DEMO_ROOM_CODE);
        return summary;
    }

    /** Goles con distribución realista: más ceros y unos que goleadas. */
    private static int goals(Random r) {
        double x = r.nextDouble();
        if (x < 0.28) return 0;
        if (x < 0.62) return 1;
        if (x < 0.84) return 2;
        if (x < 0.95) return 3;
        return 4;
    }

    /** 45% anticipadas (+24 h), 40% normales, 15% de último minuto (menos de 10 min). */
    private static Duration advance(Random r) {
        double x = r.nextDouble();
        if (x < 0.45) {
            return Duration.ofHours(25 + r.nextInt(96));
        }
        if (x < 0.85) {
            return Duration.ofMinutes(15 + r.nextInt(20 * 60));
        }
        return Duration.ofMinutes(1 + r.nextInt(9));
    }
}
