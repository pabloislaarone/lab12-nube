package pe.edu.tecsup.marcador.api.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.tecsup.marcador.api.common.Times;
import pe.edu.tecsup.marcador.api.match.MatchView;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Panel de administración: partidos, resultados, recálculo y datos de demostración. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public record MatchRequest(
            @NotBlank @Size(max = 40) String stage,
            @NotBlank @Size(max = 60) String homeTeam,
            @NotBlank @Size(max = 60) String awayTeam,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "debe tener 3 letras") String homeCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "debe tener 3 letras") String awayCode,
            @Size(max = 80) String venue,
            @NotNull Instant kickoffAt) {
    }

    public record ResultRequest(
            @NotNull @Min(0) @Max(30) Integer homeGoals,
            @NotNull @Min(0) @Max(30) Integer awayGoals) {
    }

    public record DemoRequest(@Min(2) @Max(200) Integer users) {
    }

    public record ScoringRun(long id, Long triggerMatch, String reason, String instance, int usersProcessed,
                             int matchesProcessed, long durationMs, Instant createdAt) {
    }

    private final AdminService admin;
    private final DemoDataService demo;
    private final JdbcClient jdbc;

    public AdminController(AdminService admin, DemoDataService demo, JdbcClient jdbc) {
        this.admin = admin;
        this.demo = demo;
        this.jdbc = jdbc;
    }

    @PostMapping("/matches")
    public ResponseEntity<MatchView> create(@Valid @RequestBody MatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.create(request));
    }

    @PutMapping("/matches/{id}")
    public MatchView update(@PathVariable long id, @Valid @RequestBody MatchRequest request) {
        return admin.update(id, request);
    }

    @DeleteMapping("/matches/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        admin.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/matches/{id}/result")
    public MatchView result(@PathVariable long id, @Valid @RequestBody ResultRequest request) {
        return admin.setResult(id, request.homeGoals(), request.awayGoals());
    }

    @PostMapping("/recompute")
    public ResponseEntity<Map<String, String>> recompute() {
        admin.requestRecompute();
        return ResponseEntity.accepted().body(Map.of("message", "Recálculo encolado en RabbitMQ"));
    }

    @PostMapping("/demo")
    public Map<String, Object> demo(@Valid @RequestBody(required = false) DemoRequest request) {
        int users = request == null || request.users() == null ? 20 : request.users();
        return demo.seed(users);
    }

    /** Bitácora de recálculos: muestra qué réplica del worker procesó cada evento. */
    @GetMapping("/scoring-runs")
    public List<ScoringRun> scoringRuns() {
        return jdbc.sql("""
                        SELECT id, trigger_match, reason, instance, users_processed, matches_processed,
                               duration_ms, created_at
                        FROM scoring_runs ORDER BY id DESC LIMIT 30
                        """)
                .query((rs, i) -> new ScoringRun(rs.getLong("id"),
                        rs.getObject("trigger_match") == null ? null : rs.getLong("trigger_match"),
                        rs.getString("reason"), rs.getString("instance"), rs.getInt("users_processed"),
                        rs.getInt("matches_processed"), rs.getLong("duration_ms"),
                        Times.instant(rs, "created_at")))
                .list();
    }
}
