package pe.edu.tecsup.marcador.scoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    private static final Instant KICKOFF = Instant.parse("2026-06-11T20:00:00Z");
    private final ScoringEngine engine = new ScoringEngine(ScoringConfig.defaults());

    /** Pronóstico registrado 2 horas antes: no es anticipado ni de último minuto. */
    private static MatchEntry match(long id, int rh, int ra, Integer ph, Integer pa) {
        return match(id, rh, ra, ph, pa, Duration.ofHours(2));
    }

    private static MatchEntry match(long id, int rh, int ra, Integer ph, Integer pa, Duration advance) {
        Instant kickoff = KICKOFF.plus(Duration.ofDays(id));
        Prediction prediction = ph == null ? null : new Prediction(new Score(ph, pa), kickoff.minus(advance));
        return new MatchEntry(id, kickoff, new Score(rh, ra), prediction);
    }

    @Nested
    @DisplayName("Reglas 1, 2 y 3 (modo regla más alta)")
    class BaseRules {

        @ParameterizedTest(name = "pronóstico {0}-{1} vs real {2}-{3} => {4} ({5} pts)")
        @CsvSource({
                // regla 1: exacto
                "2,1, 2,1, EXACT, 5",
                "0,0, 0,0, EXACT, 5",
                // regla 2: ganador correcto
                "1,0, 3,0, WINNER, 3",
                "0,2, 1,4, WINNER, 3",
                // empate correcto con otro marcador
                "1,1, 2,2, WINNER, 3",
                // ganador + diferencia: vale solo la más alta (3)
                "3,1, 2,0, WINNER, 3",
                // fallos
                "2,0, 0,1, MISS, 0",
                "1,1, 1,0, MISS, 0",
                "0,1, 1,1, MISS, 0"
        })
        void evaluaReglasBase(int ph, int pa, int rh, int ra, BaseRule rule, int points) {
            var result = engine.evaluateBase(new Score(ph, pa), new Score(rh, ra));
            assertThat(result.rule()).isEqualTo(rule);
            assertThat(result.points()).isEqualTo(points);
        }

        @Test
        void detectaDiferenciaDeGolesAunqueNoSumeEnModoMasAlta() {
            var result = engine.evaluateBase(new Score(3, 1), new Score(2, 0));
            assertThat(result.goalDiffHit()).isTrue();
            assertThat(result.winnerHit()).isTrue();
        }

        @Test
        void sinPronosticoNoSumaNada() {
            var result = engine.evaluateBase(null, new Score(1, 0));
            assertThat(result.rule()).isEqualTo(BaseRule.NOT_PREDICTED);
            assertThat(result.points()).isZero();
        }
    }

    @Nested
    @DisplayName("Modo acumulativo (configurable)")
    class Cumulative {
        private final ScoringEngine cumulative =
                new ScoringEngine(ScoringConfig.defaults().withCombineMode(CombineMode.CUMULATIVE));

        @Test
        void ganadorMasDiferenciaSumaCinco() {
            var r = cumulative.evaluateBase(new Score(3, 1), new Score(2, 0));
            assertThat(r.rule()).isEqualTo(BaseRule.GOAL_DIFF);
            assertThat(r.points()).isEqualTo(5);
        }

        @Test
        void soloGanadorSumaTres() {
            var r = cumulative.evaluateBase(new Score(1, 0), new Score(3, 0));
            assertThat(r.points()).isEqualTo(3);
        }

        @Test
        void exactoSigueSiendoCinco() {
            var r = cumulative.evaluateBase(new Score(2, 1), new Score(2, 1));
            assertThat(r.points()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("Regla 4: bonus por racha")
    class Streak {

        @Test
        void tresAciertosSeguidosSumanDosPuntos() {
            var summary = engine.score(List.of(
                    match(1, 1, 0, 2, 0),   // ganador: 3
                    match(2, 0, 0, 1, 1),   // empate: 3
                    match(3, 2, 1, 2, 1))); // exacto: 5 + racha 2
            assertThat(summary.matches().get(2).streakBonus()).isEqualTo(2);
            assertThat(summary.totalPoints()).isEqualTo(3 + 3 + 5 + 2);
            assertThat(summary.currentStreak()).isEqualTo(3);
        }

        @Test
        void seisAciertosSeguidosDanDosBonus() {
            List<MatchEntry> matches = new ArrayList<>();
            for (int i = 1; i <= 6; i++) {
                matches.add(match(i, 1, 0, 2, 0));
            }
            var summary = engine.score(matches);
            assertThat(summary.streakBonusPoints()).isEqualTo(4);
            assertThat(summary.totalPoints()).isEqualTo(6 * 3 + 4);
        }

        @Test
        void unFalloCortaLaRacha() {
            var summary = engine.score(List.of(
                    match(1, 1, 0, 1, 0),
                    match(2, 1, 0, 1, 0),
                    match(3, 1, 0, 0, 1),   // falla
                    match(4, 1, 0, 1, 0)));
            assertThat(summary.streakBonusPoints()).isZero();
            assertThat(summary.currentStreak()).isEqualTo(1);
            assertThat(summary.bestStreak()).isEqualTo(2);
        }

        @Test
        void noPronosticarCortaLaRacha() {
            var summary = engine.score(List.of(
                    match(1, 1, 0, 1, 0),
                    match(2, 1, 0, 1, 0),
                    match(3, 1, 0, null, null), // no pronosticó
                    match(4, 1, 0, 1, 0)));
            assertThat(summary.matches().get(2).rule()).isEqualTo(BaseRule.NOT_PREDICTED);
            assertThat(summary.streakBonusPoints()).isZero();
            assertThat(summary.currentStreak()).isEqualTo(1);
        }

        @Test
        void laRachaSeEvaluaEnOrdenCronologico() {
            // se entregan desordenados: el engine debe ordenarlos por fecha
            var summary = engine.score(List.of(
                    match(3, 1, 0, 1, 0),
                    match(1, 1, 0, 1, 0),
                    match(2, 1, 0, 1, 0)));
            assertThat(summary.matches()).extracting(MatchScore::matchId).containsExactly(1L, 2L, 3L);
            assertThat(summary.matches().get(2).streakBonus()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Regla 5: predicción anticipada y último minuto")
    class Early {

        @Test
        void masDe24HorasSumaUnPunto() {
            var summary = engine.score(List.of(match(1, 2, 1, 2, 1, Duration.ofHours(25))));
            MatchScore s = summary.matches().get(0);
            assertThat(s.early()).isTrue();
            assertThat(s.earlyBonus()).isEqualTo(1);
            assertThat(s.total()).isEqualTo(6);
        }

        @Test
        void exactamente24HorasNoEsMasDe24() {
            var summary = engine.score(List.of(match(1, 2, 1, 2, 1, Duration.ofHours(24))));
            assertThat(summary.matches().get(0).earlyBonus()).isZero();
        }

        @Test
        void anticipadoPeroFalladoNoSumaPorDefecto() {
            var summary = engine.score(List.of(match(1, 2, 1, 0, 3, Duration.ofDays(3))));
            assertThat(summary.matches().get(0).earlyBonus()).isZero();
            assertThat(summary.totalPoints()).isZero();
        }

        @Test
        void ultimoMinutoSoloRecibePuntosBase() {
            var summary = engine.score(List.of(match(1, 2, 1, 2, 1, Duration.ofMinutes(5))));
            MatchScore s = summary.matches().get(0);
            assertThat(s.lastMinute()).isTrue();
            assertThat(s.earlyBonus()).isZero();
            assertThat(s.total()).isEqualTo(5);
        }

        @Test
        void ultimoMinutoNoAvanzaNiCortaLaRacha() {
            var summary = engine.score(List.of(
                    match(1, 1, 0, 1, 0),
                    match(2, 1, 0, 1, 0),
                    match(3, 1, 0, 1, 0, Duration.ofMinutes(3)),   // último minuto: neutral
                    match(4, 1, 0, 1, 0)));                        // tercer acierto válido
            assertThat(summary.matches().get(2).streakBonus()).isZero();
            assertThat(summary.matches().get(3).streakBonus()).isEqualTo(2);
        }

        @Test
        void todasLasReglasJuntas() {
            var summary = engine.score(List.of(
                    match(1, 1, 0, 2, 0, Duration.ofDays(2)),   // ganador 3 + anticipada 1
                    match(2, 2, 2, 1, 1, Duration.ofHours(5)),  // empate 3
                    match(3, 3, 1, 3, 1, Duration.ofDays(1).plusMinutes(1)))); // exacto 5 + anticipada 1 + racha 2
            assertThat(summary.totalPoints()).isEqualTo(4 + 3 + 8);
            assertThat(summary.exactHits()).isEqualTo(1);
            assertThat(summary.winnerHits()).isEqualTo(3);
            assertThat(summary.earlyBonusPoints()).isEqualTo(2);
            assertThat(summary.streakBonusPoints()).isEqualTo(2);
        }
    }

    @Test
    void esIdempotente() {
        List<MatchEntry> matches = List.of(match(1, 1, 0, 1, 0), match(2, 0, 2, 1, 3));
        assertThat(engine.score(matches)).isEqualTo(engine.score(matches));
    }
}
