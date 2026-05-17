package it.dsms.grabber.curated;

import it.dsms.grabber.crea.CreaFood;
import java.util.*;

public class IngredientMatcher {

    private final List<CreaFood> creaFoods;

    public IngredientMatcher(List<CreaFood> creaFoods) {
        this.creaFoods = creaFoods;
    }

    public static class MatchResult {
        public final CreaFood food;
        public final double confidence;
        public final String method;

        public MatchResult(CreaFood food, double confidence, String method) {
            this.food = food;
            this.confidence = confidence;
            this.method = method;
        }
    }

    /**
     * Ingredienti condimento/acqua senza codice CREA autonomo e 0 kcal rilevanti.
     * Vengono trattati come "matched silenzioso": yield_factor=0, kcal=0,
     * NON contano come unmatched nella coverage.
     */
    private static final Set<String> IGNORABLE = Set.of(
            "sale", "sale fino", "sale grosso", "sale marino", "sale integrale",
            "acqua", "acqua fredda", "acqua calda", "bicarbonato", "lievito in polvere"
    );

    /**
     * Tenta di matchare il nome ingrediente a un CreaFood.
     * Priorita': ignorable list > hint refs > exact > contains > token overlap.
     * Ritorna null se nessun match supera la soglia.
     */
    public MatchResult match(String ingredientName, List<CuratedRecipeTargetCreaRef> hintRefs) {
        String normIngredient = normalize(ingredientName);
        if (normIngredient.isEmpty()) return null;

        // 0. Ingredienti ignorabili (sale, acqua, bicarbonato...): 0 kcal, 0 peso,
        //    ma NON contano come unmatched → restituisce un match fittizio con food=null.
        //    Il chiamante deve gestire food==null come "ignorabile".
        if (IGNORABLE.contains(normIngredient)) return new MatchResult(null, 1.0, "ignorable");

        // 1. Hint match: se il nome ingrediente e' vicino a uno dei ref esistenti.
        //    Confronta sia contro il label umano (es. "olio EVO") sia contro il nome CREA
        //    (es. "Olio extravergine d'oliva") prendendo il massimo — evita falsi negativi
        //    quando il label e' un acronimo o abbreviazione.
        if (hintRefs != null) {
            for (CuratedRecipeTargetCreaRef ref : hintRefs) {
                if (ref.creaCode == null) continue;
                String normLabel    = normalize(ref.label     != null ? ref.label     : "");
                String normCreaName = normalize(ref.creaNameIt != null ? ref.creaNameIt : "");
                double sim = Math.max(tokenOverlap(normIngredient, normLabel),
                                      tokenOverlap(normIngredient, normCreaName));
                if (sim >= 0.6) {
                    CreaFood food = findByCode(ref.creaCode);
                    if (food != null) return new MatchResult(food, 0.90 * sim + 0.05, "hint_ref");
                }
            }
        }

        // 2. Exact contains match
        MatchResult best = null;
        for (CreaFood f : creaFoods) {
            String normCrea = normalize(f.nameIt);
            double score;
            String method;

            if (normCrea.equals(normIngredient)) {
                score = 1.0; method = "exact";
            } else if (normCrea.contains(normIngredient) && normIngredient.length() >= 6) {
                // soglia 6: evita falsi positivi con parole corte (es. "sale" in "acciuga sotto sale")
                score = 0.85; method = "contains_crea";
            } else if (normIngredient.contains(normCrea) && normCrea.length() >= 6) {
                score = 0.80; method = "contains_ingredient";
            } else {
                double overlap = tokenOverlap(normIngredient, normCrea);
                // soglia 0.60: evita falsi positivi borderline come
                // "vino bianco secco" → "Sogliola al vino bianco" (overlap 0.50)
                if (overlap >= 0.60) { score = overlap * 0.75; method = "token_overlap"; }
                else continue;
            }

            if (best == null || score > best.confidence) {
                best = new MatchResult(f, score, method);
                if (score >= 1.0) break;
            }
        }

        return (best != null && best.confidence >= 0.5) ? best : null;
    }

    // -------------------------------------------------------------------------

    private CreaFood findByCode(String code) {
        String trimmed = code.trim();
        return creaFoods.stream()
                .filter(f -> trimmed.equals(f.code != null ? f.code.trim() : null))
                .findFirst().orElse(null);
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ITALIAN)
                .replaceAll("[,.]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    static double tokenOverlap(String a, String b) {
        Set<String> tokA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> tokB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        if (tokA.isEmpty() || tokB.isEmpty()) return 0.0;
        // rimuovi stopwords italiane comuni
        Set<String> stop = new HashSet<>(Arrays.asList("di", "al", "alla", "con", "e", "o", "in", "a", "da", "del", "della"));
        tokA.removeAll(stop);
        tokB.removeAll(stop);
        if (tokA.isEmpty() || tokB.isEmpty()) return 0.0;
        long common = tokA.stream().filter(tokB::contains).count();
        return (double) common / Math.max(tokA.size(), tokB.size());
    }
}
