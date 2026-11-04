package pe.edu.tecsup.polla.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pe.edu.tecsup.polla.scoring.CombineMode;
import pe.edu.tecsup.polla.scoring.ScoringConfig;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String instanceId, Jwt jwt, Admin admin, Scoring scoring) {

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
