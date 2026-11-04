package pe.edu.tecsup.polla.api.match;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.tecsup.polla.api.common.ApiException;
import pe.edu.tecsup.polla.api.security.AuthUser;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    public record PredictionRequest(
            @NotNull @Min(0) @Max(30) Integer homeGoals,
            @NotNull @Min(0) @Max(30) Integer awayGoals) {
    }

    private final MatchRepository matches;
    private final PredictionService predictions;

    public MatchController(MatchRepository matches, PredictionService predictions) {
        this.matches = matches;
        this.predictions = predictions;
    }

    @GetMapping
    public List<MatchView> list(@AuthenticationPrincipal AuthUser user,
                                @RequestParam(required = false) String status) {
        String filter = status == null || status.isBlank() ? null : status.toUpperCase();
        return matches.findAll(user == null ? null : user.id(), filter);
    }

    @GetMapping("/{id}")
    public MatchView get(@AuthenticationPrincipal AuthUser user, @PathVariable long id) {
        return matches.findView(id, user == null ? null : user.id())
                .orElseThrow(() -> ApiException.notFound("Partido no encontrado"));
    }

    @PutMapping("/{id}/prediction")
    public MatchView predict(@AuthenticationPrincipal AuthUser user, @PathVariable long id,
                             @Valid @RequestBody PredictionRequest request) {
        predictions.save(user, id, request.homeGoals(), request.awayGoals());
        return matches.findView(id, user.id()).orElseThrow();
    }
}
