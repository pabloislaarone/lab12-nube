package pe.edu.tecsup.marcador.api.insights;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.tecsup.marcador.api.common.ApiException;
import pe.edu.tecsup.marcador.api.insights.InsightEngine.Insights;
import pe.edu.tecsup.marcador.api.security.AuthUser;

@RestController
public class InsightController {

    private final InsightService insights;

    public InsightController(InsightService insights) {
        this.insights = insights;
    }

    /** Análisis personalizado del participante (con IA si hay clave configurada). */
    @GetMapping("/api/stats/me/insights")
    public Insights mine(@AuthenticationPrincipal AuthUser user) {
        if (user.isAdmin()) {
            throw ApiException.badRequest("El administrador no tiene pronósticos que analizar");
        }
        return insights.forUser(user.id());
    }
}
