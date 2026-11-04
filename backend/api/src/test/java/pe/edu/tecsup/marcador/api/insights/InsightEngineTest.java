package pe.edu.tecsup.marcador.api.insights;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pe.edu.tecsup.marcador.api.insights.InsightEngine.Features;
import pe.edu.tecsup.marcador.api.insights.InsightEngine.Insights;
import pe.edu.tecsup.marcador.api.insights.InsightEngine.Row;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias del análisis de estadísticas (sin Spring ni IA real). */
class InsightEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final List<Row> ROWS = List.of(
            new Row("EXACT", true, true, false, 2, 1, 2, 1),      // exacto anticipado
            new Row("WINNER", true, false, false, 1, 0, 3, 0),    // ganador, normal
            new Row("MISS", false, false, true, 1, 1, 0, 2),      // falla de último minuto
            new Row("NOT_PREDICTED", false, false, false, null, null, 1, 0)); // sin pronóstico

    private Features sample() {
        return InsightEngine.features(ROWS, 9, 2, 10, 0, 2, 1, 0);
    }

    @Test
    void calculaLosIndicadores() {
        Features f = sample();
        assertThat(f.finished()).isEqualTo(4);
        assertThat(f.predicted()).isEqualTo(3);
        assertThat(f.missing()).isEqualTo(1);
        assertThat(f.exactHits()).isEqualTo(1);
        assertThat(f.winnerHits()).isEqualTo(2);
        assertThat(f.accuracy()).isEqualTo(66.7);
        assertThat(f.earlyCount()).isEqualTo(1);
        assertThat(f.earlyHits()).isEqualTo(1);
        assertThat(f.lastMinuteCount()).isEqualTo(1);
        assertThat(f.predHomeWins()).isEqualTo(2);
        assertThat(f.predDraws()).isEqualTo(1);
        assertThat(f.realAwayWins()).isEqualTo(1);
    }

    @Test
    void analisisAutomaticoGeneraHallazgosYConsejo() {
        Insights result = InsightEngine.ruleBased(sample());
        assertThat(result.ai()).isFalse();
        assertThat(result.items()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(result.items().get(0).text()).contains("2 de 3");
        // dejó un partido sin pronosticar: el consejo debe mencionarlo
        assertThat(result.tip()).contains("sin pronóstico");
    }

    @Test
    void sinPartidosFinalizadosLoIndica() {
        Insights result = InsightEngine.ruleBased(InsightEngine.features(List.of(), 0, 1, 1, 0, 0, 0, 0));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).title()).contains("no hay resultados");
    }

    @Test
    void interpretaLaRespuestaDeLaIaAunqueVengaConTextoAlrededor() {
        String raw = """
                Claro, aquí está:
                ```json
                {"insights":[{"title":"Buen ojo","text":"Aciertas el ganador seguido."},
                             {"title":"Más anticipación","text":"Registra antes."},
                             {"title":"Racha","text":"Cuida tu racha."}],
                 "tip":"Pronostica todos los partidos."}
                ```""";
        var parsed = InsightEngine.parseAi(raw, "IA · test", mapper);
        assertThat(parsed).isPresent();
        assertThat(parsed.get().ai()).isTrue();
        assertThat(parsed.get().items()).hasSize(3);
        assertThat(parsed.get().source()).isEqualTo("IA · test");
    }

    @Test
    void respuestaInvalidaDeLaIaSeDescarta() {
        assertThat(InsightEngine.parseAi("no es json", "IA", mapper)).isEmpty();
        assertThat(InsightEngine.parseAi("{\"insights\":[]}", "IA", mapper)).isEmpty();
        assertThat(InsightEngine.parseAi(null, "IA", mapper)).isEmpty();
    }

    @Test
    void elPromptIncluyeLosIndicadores() {
        String prompt = InsightEngine.userPrompt(sample(), mapper);
        assertThat(prompt).contains("\"winnerHits\":2").contains("\"accuracy\":66.7");
    }
}
