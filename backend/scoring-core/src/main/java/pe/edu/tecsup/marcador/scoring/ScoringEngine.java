package pe.edu.tecsup.marcador.scoring;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Motor de puntuación. Es una función pura: misma entrada, misma salida.
 * Por eso se puede recalcular todo cuantas veces sea necesario (idempotente).
 *
 * <p>Reglas implementadas:</p>
 * <ol>
 *   <li>Resultado exacto: 5 puntos.</li>
 *   <li>Ganador (o empate) correcto: 3 puntos.</li>
 *   <li>Diferencia de goles correcta: 2 puntos.</li>
 *   <li>Racha: cada 3 partidos seguidos acertando al menos el ganador suma 2 puntos extra.
 *       Un partido sin pronóstico o fallado corta la racha.</li>
 *   <li>Anticipación: más de 24 h antes del partido suma 1 punto extra. Lo registrado en los
 *       últimos 10 minutos solo recibe puntos base (no suma bonus ni avanza la racha).</li>
 * </ol>
 */
public final class ScoringEngine {

    private final ScoringConfig config;

    public ScoringEngine(ScoringConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public ScoringConfig config() {
        return config;
    }

    /** Clasificación del pronóstico según las reglas 1, 2 y 3. */
    public record BaseResult(BaseRule rule, int points, boolean winnerHit, boolean goalDiffHit) {
    }

    /** Aplica las reglas base 1-3 a un pronóstico contra el resultado real. */
    public BaseResult evaluateBase(Score predicted, Score actual) {
        if (predicted == null) {
            return new BaseResult(BaseRule.NOT_PREDICTED, 0, false, false);
        }
        boolean exact = predicted.equals(actual);
        boolean winnerHit = predicted.outcome() == actual.outcome();
        boolean goalDiffHit = predicted.goalDifference() == actual.goalDifference();

        if (exact) {
            return new BaseResult(BaseRule.EXACT, config.exactPoints(), true, true);
        }

        if (config.combineMode() == CombineMode.CUMULATIVE) {
            int points = (winnerHit ? config.winnerPoints() : 0) + (goalDiffHit ? config.goalDiffPoints() : 0);
            BaseRule rule = goalDiffHit ? BaseRule.GOAL_DIFF : (winnerHit ? BaseRule.WINNER : BaseRule.MISS);
            return new BaseResult(rule, points, winnerHit, goalDiffHit);
        }

        // HIGHEST: solo vale la regla que da más puntos
        int best = 0;
        BaseRule rule = BaseRule.MISS;
        if (winnerHit && config.winnerPoints() > best) {
            best = config.winnerPoints();
            rule = BaseRule.WINNER;
        }
        if (goalDiffHit && config.goalDiffPoints() > best) {
            best = config.goalDiffPoints();
            rule = BaseRule.GOAL_DIFF;
        }
        return new BaseResult(rule, best, winnerHit, goalDiffHit);
    }

    /** true si se registró con MÁS de 24 h de anticipación. */
    public boolean isEarly(Prediction prediction, MatchEntry match) {
        Duration advance = Duration.between(prediction.submittedAt(), match.kickoff());
        return advance.compareTo(config.earlyThreshold()) > 0;
    }

    /** true si se registró dentro de los últimos 10 minutos antes del partido. */
    public boolean isLastMinute(Prediction prediction, MatchEntry match) {
        Duration advance = Duration.between(prediction.submittedAt(), match.kickoff());
        return advance.compareTo(config.lastMinuteWindow()) < 0;
    }

    /**
     * Calcula los puntos de un participante. Los partidos se ordenan por fecha de inicio
     * para evaluar la racha en orden cronológico.
     *
     * @param finishedMatches todos los partidos finalizados (con o sin pronóstico del participante)
     */
    public ScoreSummary score(List<MatchEntry> finishedMatches) {
        List<MatchEntry> ordered = new ArrayList<>(finishedMatches);
        ordered.sort(Comparator.comparing(MatchEntry::kickoff).thenComparingLong(MatchEntry::matchId));

        List<MatchScore> scores = new ArrayList<>(ordered.size());
        int streak = 0;
        int bestStreak = 0;
        int total = 0;
        int predicted = 0;
        int exactHits = 0;
        int winnerHits = 0;
        int earlyPoints = 0;
        int streakPoints = 0;

        for (MatchEntry match : ordered) {
            Prediction prediction = match.prediction();

            if (prediction == null) {
                // Regla 4: no pronosticar corta la racha
                streak = 0;
                scores.add(new MatchScore(match.matchId(), BaseRule.NOT_PREDICTED, 0,
                        false, false, false, false, 0, 0, streak, 0));
                continue;
            }

            predicted++;
            BaseResult base = evaluateBase(prediction.score(), match.result());
            boolean lastMinute = isLastMinute(prediction, match);
            boolean early = !lastMinute && isEarly(prediction, match);

            // Regla 5: anticipación
            int earlyBonus = 0;
            if (early && (!config.earlyBonusRequiresHit() || base.points() > 0)) {
                earlyBonus = config.earlyBonus();
            }

            // Regla 4: racha (lo de último minuto no la avanza ni la corta)
            int streakBonus = 0;
            if (!lastMinute) {
                if (base.winnerHit()) {
                    streak++;
                    if (streak % config.streakLength() == 0) {
                        streakBonus = config.streakBonus();
                    }
                } else {
                    streak = 0;
                }
            }
            bestStreak = Math.max(bestStreak, streak);

            int matchTotal = base.points() + earlyBonus + streakBonus;
            total += matchTotal;
            earlyPoints += earlyBonus;
            streakPoints += streakBonus;
            if (base.rule() == BaseRule.EXACT) {
                exactHits++;
            }
            if (base.winnerHit()) {
                winnerHits++;
            }

            scores.add(new MatchScore(match.matchId(), base.rule(), base.points(),
                    base.winnerHit(), base.goalDiffHit(), early, lastMinute,
                    earlyBonus, streakBonus, streak, matchTotal));
        }

        return new ScoreSummary(List.copyOf(scores), total, predicted, exactHits, winnerHits,
                streak, bestStreak, earlyPoints, streakPoints);
    }
}
