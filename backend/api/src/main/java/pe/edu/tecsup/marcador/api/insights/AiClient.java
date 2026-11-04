package pe.edu.tecsup.marcador.api.insights;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import pe.edu.tecsup.marcador.api.config.AppProperties;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cliente mínimo para modelos de lenguaje. Soporta Claude (Anthropic) y Gemini (Google).
 * Si no hay clave configurada, la IA queda desactivada y se usa el análisis por reglas.
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    private final String provider;
    private final String apiKey;
    private final String model;
    private final RestClient http;

    public AiClient(AppProperties props) {
        AppProperties.Ai ai = props.ai();
        this.provider = ai == null || ai.provider() == null || ai.provider().isBlank()
                ? "anthropic" : ai.provider().trim().toLowerCase();
        this.apiKey = ai == null || ai.apiKey() == null ? "" : ai.apiKey().trim();
        String configured = ai == null || ai.model() == null ? "" : ai.model().trim();
        this.model = !configured.isEmpty() ? configured
                : "gemini".equals(provider) ? "gemini-2.5-flash" : "claude-haiku-5-5";

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(20_000);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return !apiKey.isEmpty();
    }

    /** Ej. "IA · claude-haiku-5-5" */
    public String label() {
        return "IA · " + model;
    }

    public Optional<String> complete(String system, String user) {
        if (!enabled()) return Optional.empty();
        try {
            return "gemini".equals(provider) ? gemini(system, user) : anthropic(system, user);
        } catch (Exception ex) {
            log.warn("La IA no respondió ({}): se usa el análisis automático", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> anthropic(String system, String user) {
        JsonNode res = http.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "model", model,
                        "max_tokens", 700,
                        "system", system,
                        "messages", List.of(Map.of("role", "user", "content", user))))
                .retrieve()
                .body(JsonNode.class);
        return Optional.ofNullable(res).map(r -> r.path("content").path(0).path("text").asText(null));
    }

    private Optional<String> gemini(String system, String user) {
        JsonNode res = http.post()
                .uri("https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent", model)
                .header("x-goog-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "systemInstruction", Map.of("parts", List.of(Map.of("text", system))),
                        "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", user)))),
                        "generationConfig", Map.of("responseMimeType", "application/json", "maxOutputTokens", 700)))
                .retrieve()
                .body(JsonNode.class);
        return Optional.ofNullable(res)
                .map(r -> r.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText(null));
    }
}
