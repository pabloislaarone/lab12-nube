package pe.edu.tecsup.marcador.api.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lógica pura del análisis: calcula los indicadores del participante, arma el prompt para la IA,
 * interpreta su respuesta y, si no hay IA disponible, genera el análisis con reglas.
 * No depende de Spring para poder probarse con pruebas unitarias.
 */
public final class InsightEngine {

    private InsightEngine() {
    }

    /** Un partido finalizado visto desde el participante. Los goles pronosticados son null si no pronosticó. */
    public record Row(String rule, boolean winnerHit, boolean early, boolean lastMinute,
                      Integer predHome, Integer predAway, int realHome, int realAway) {
    }

    public record Features(
            int finished, int predicted, int missing, int exactHits, int winnerHits, double accuracy,
            int totalPoints, int position, int participants, int currentStreak, int bestStreak,
            int earlyCount, int earlyHits, int normalCount, int normalHits, int lastMinuteCount,
            int predHomeWins, int predDraws, int predAwayWins,
            int realHomeWins, int realDraws, int realAwayWins,
            double avgPredGoals, double avgRealGoals, int earlyPoints, int streakPoints) {
    }

    public record Insight(String title, String text) {
    }

    public record Insights(boolean ai, String source, List<Insight> items, String tip) {
    }

    // ------------------------------------------------------------------ indicadores

    public static Features features(List<Row> rows, int totalPoints, int position, int participants,
                                    int currentStreak, int bestStreak, int earlyPoints, int streakPoints) {
        int predicted = 0, missing = 0, exact = 0, winner = 0;
        int earlyCount = 0, earlyHits = 0, normalCount = 0, normalHits = 0, lastMinute = 0;
        int pH = 0, pD = 0, pA = 0, rH = 0, rD = 0, rA = 0;
        int predGoals = 0, realGoals = 0;

        for (Row r : rows) {
            if (r.predHome() == null) {
                missing++;
                continue;
            }
            predicted++;
            if ("EXACT".equals(r.rule())) exact++;
            if (r.winnerHit()) winner++;
            if (r.early()) {
                earlyCount++;
                if (r.winnerHit()) earlyHits++;
            } else {
                normalCount++;
                if (r.winnerHit()) normalHits++;
            }
            if (r.lastMinute()) lastMinute++;

            int p = Integer.signum(r.predHome() - r.predAway());
            int a = Integer.signum(r.realHome() - r.realAway());
            if (p > 0) pH++; else if (p == 0) pD++; else pA++;
            if (a > 0) rH++; else if (a == 0) rD++; else rA++;
            predGoals += r.predHome() + r.predAway();
            realGoals += r.realHome() + r.realAway();
        }
        double accuracy = predicted == 0 ? 0 : round1(100.0 * winner / predicted);
        double avgPred = predicted == 0 ? 0 : round1((double) predGoals / predicted);
        double avgReal = predicted == 0 ? 0 : round1((double) realGoals / predicted);

        return new Features(rows.size(), predicted, missing, exact, winner, accuracy, totalPoints, position,
                participants, currentStreak, bestStreak, earlyCount, earlyHits, normalCount, normalHits,
                lastMinute, pH, pD, pA, rH, rD, rA, avgPred, avgReal, earlyPoints, streakPoints);
    }

    // ------------------------------------------------------------------ análisis sin IA

    /** Análisis por reglas: se usa cuando no hay clave de IA o la IA no responde. */
    public static Insights ruleBased(Features f) {
        List<Insight> items = new ArrayList<>();

        if (f.finished() == 0) {
            items.add(new Insight("Todavía no hay resultados",
                    "Cuando termine el primer partido verás aquí el análisis de tus pronósticos."));
            return new Insights(false, "Análisis automático", items,
                    "Pronostica con más de 24 h de anticipación para sumar el punto extra.");
        }
        if (f.predicted() == 0) {
            items.add(new Insight("Sin pronósticos en partidos finalizados",
                    "Ya terminaron " + f.finished() + " partidos y no pronosticaste ninguno, así que tu racha está en cero."));
            return new Insights(false, "Análisis automático", items,
                    "Entra a Partidos y registra tus marcadores antes del pitazo inicial.");
        }

        items.add(new Insight("Tu rendimiento",
                String.format(Locale.ROOT, "Acertaste el ganador en %d de %d partidos (%s %%) y clavaste %d marcador%s exacto%s. Vas en el puesto %d de %d.",
                        f.winnerHits(), f.predicted(), fmt(f.accuracy()), f.exactHits(),
                        f.exactHits() == 1 ? "" : "es", f.exactHits() == 1 ? "" : "s", f.position(), f.participants())));

        // Tendencia: ¿favorece a los locales, a los visitantes o a los empates?
        int maxPred = Math.max(f.predHomeWins(), Math.max(f.predDraws(), f.predAwayWins()));
        String bias = maxPred == f.predHomeWins() ? "victorias locales"
                : maxPred == f.predAwayWins() ? "victorias visitantes" : "empates";
        int realOfBias = maxPred == f.predHomeWins() ? f.realHomeWins()
                : maxPred == f.predAwayWins() ? f.realAwayWins() : f.realDraws();
        items.add(new Insight("Tu tendencia",
                "Sueles pronosticar " + bias + " (" + maxPred + " de " + f.predicted() + "), y en esos partidos el resultado real fue así "
                        + realOfBias + " veces. Prediciste " + fmt(f.avgPredGoals()) + " goles por partido y en promedio hubo "
                        + fmt(f.avgRealGoals()) + "."));

        if (f.earlyCount() > 0 && f.normalCount() > 0) {
            double earlyRate = 100.0 * f.earlyHits() / f.earlyCount();
            double normalRate = 100.0 * f.normalHits() / f.normalCount();
            String better = earlyRate >= normalRate ? "anticipados" : "de última hora";
            items.add(new Insight("Anticipación",
                    String.format(Locale.ROOT, "Con pronósticos anticipados aciertas %s %% y con los demás %s %%: te va mejor con los %s. La anticipación ya te dio %d punto%s extra.",
                            fmt(round1(earlyRate)), fmt(round1(normalRate)), better, f.earlyPoints(),
                            f.earlyPoints() == 1 ? "" : "s")));
        }

        if (f.missing() > 0 || f.bestStreak() >= 3) {
            items.add(new Insight("Rachas",
                    "Tu mejor racha fue de " + f.bestStreak() + " aciertos seguidos y te dio " + f.streakPoints()
                            + " puntos de bonus." + (f.missing() > 0
                            ? " Dejaste " + f.missing() + " partido" + (f.missing() == 1 ? "" : "s") + " sin pronosticar, y eso corta la racha."
                            : "")));
        }

        String tip;
        if (f.missing() > 0) {
            tip = "No dejes partidos sin pronóstico: uno vacío reinicia tu racha.";
        } else if (f.earlyCount() < f.predicted() / 2) {
            tip = "Registra tus pronósticos con más de 24 h de anticipación para sumar el punto extra.";
        } else if (Math.abs(f.avgPredGoals() - f.avgRealGoals()) >= 0.8) {
            tip = f.avgPredGoals() > f.avgRealGoals()
                    ? "Estás pronosticando partidos con más goles de los que realmente hay: prueba marcadores más cortos."
                    : "Los partidos están teniendo más goles de los que pronosticas: anímate con marcadores más abiertos.";
        } else {
            tip = "Mantén la constancia: cada 3 aciertos seguidos suman 2 puntos extra.";
        }
        return new Insights(false, "Análisis automático", items, tip);
    }

    // ------------------------------------------------------------------ IA

    public static final String SYSTEM_PROMPT = """
            Eres un analista deportivo de "Marcador 26", una app educativa de pronósticos del Mundial 2026 (sin dinero real).
            Recibes los indicadores de un participante y escribes un análisis breve, en español neutro, cercano y concreto.
            Reglas de puntos: exacto 5, ganador 3, diferencia 2 (solo vale la más alta), +2 por cada 3 aciertos seguidos,
            +1 si el pronóstico se registró con más de 24 h de anticipación; no pronosticar corta la racha.
            Usa solo los números que recibes, no inventes datos ni nombres de equipos.
            Responde ÚNICAMENTE con JSON válido con esta forma:
            {"insights":[{"title":"máx. 4 palabras","text":"máx. 220 caracteres"}], "tip":"un consejo accionable, máx. 160 caracteres"}
            Incluye exactamente 3 insights.
            """;

    public static String userPrompt(Features f, ObjectMapper mapper) {
        try {
            return "Indicadores del participante (JSON):\n" + mapper.writeValueAsString(f);
        } catch (Exception e) {
            return "Indicadores del participante: " + f;
        }
    }

    /** Interpreta la respuesta de la IA. Devuelve vacío si no tiene la forma esperada. */
    public static Optional<Insights> parseAi(String raw, String source, ObjectMapper mapper) {
        if (raw == null) return Optional.empty();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return Optional.empty();
        try {
            JsonNode root = mapper.readTree(raw.substring(start, end + 1));
            List<Insight> items = new ArrayList<>();
            for (JsonNode n : root.path("insights")) {
                String title = n.path("title").asText("").trim();
                String text = n.path("text").asText("").trim();
                if (!title.isEmpty() && !text.isEmpty()) {
                    items.add(new Insight(limit(title, 60), limit(text, 300)));
                }
            }
            String tip = root.path("tip").asText("").trim();
            if (items.isEmpty() || tip.isEmpty()) return Optional.empty();
            return Optional.of(new Insights(true, source, List.copyOf(items.subList(0, Math.min(4, items.size()))),
                    limit(tip, 220)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v).replace('.', ',');
    }

    private static String limit(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
