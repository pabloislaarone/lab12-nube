package pe.edu.tecsup.marcador.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pe.edu.tecsup.marcador.scoring.CombineMode;
import pe.edu.tecsup.marcador.scoring.ScoringConfig;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String instanceId, Jwt jwt, Admin admin, Scoring scoring, Ai ai) {

    /** Estadísticas con IA: provider = anthropic | gemini. Sin apiKey se usa el análisis automático. */
    public record Ai(String provider, String apiKey, String model) {
    }

    public record Jwt(String secret, long ttlHours) {
    }

    public record Admin(String name, String email, String password) {
    }

    public record Scoring(int exactPoints, int winnerPoints, int goalDiffPoints,
                          int streakLength, int streakBonus, int earlyBonus,
                          long earlyThresholdHours, long lastMinuteWindowMinutes,
                          CombineMode combineMode, boolean earlyBonusRequiresHit) {

        public ScoringConfig toConfig() {
            return new ScoringConfig(exactPoints, winnerPoints, goalDiffPoints, streakLength, streakBonus,
                    earlyBonus, Duration.ofHours(earlyThresholdHours), Duration.ofMinutes(lastMinuteWindowMinutes),
                    combineMode, earlyBonusRequiresHit);
        }
    }
}
