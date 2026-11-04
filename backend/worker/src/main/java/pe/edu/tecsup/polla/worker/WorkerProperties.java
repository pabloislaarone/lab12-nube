package pe.edu.tecsup.polla.worker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pe.edu.tecsup.polla.scoring.CombineMode;
import pe.edu.tecsup.polla.scoring.ScoringConfig;

import java.time.Duration;

@ConfigurationProperties(prefix = "worker")
public record WorkerProperties(String instanceId, Scoring scoring) {

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
