package it.dsms.grabber.engine.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.dsms.grabber.engine.curated.RecipeIngredientCandidate;

/**
 * Rappresentazione di un ingrediente nel prepared_dishes_seed.json.
 *
 * Scelta progettuale:
 * - crea_id usa il prefisso "crea_" → compatibile con crea_seed.json,
 *   quindi Flutter può fare lookup diretto su CreaFoodRepository.
 * - grams_per_portion = grams_raw / declared_servings (già diviso per porzione).
 * - Gli ingredienti ignorabili (sale, acqua) e gli aromi a peso zero
 *   (yield_factor=0) sono inclusi per completezza ma con grams_per_portion
 *   effettivo (non scalato al peso del piatto).
 * - I campi interni alla curation (match_method, confidence, candidate_id)
 *   NON sono esportati: sono dati di processo, non dati di dominio.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PreparedDishIngredientRecord {

    /** "crea_" + crea_code — null se ingrediente non matchato in CREA. */
    @JsonProperty("crea_id")           public String creaId;

    /** Nome ingrediente così come generato dal LLM (normalizzato). */
    @JsonProperty("name_raw")          public String nameRaw;

    /** Grammi crudi per porzione = grams_raw / declared_servings. */
    @JsonProperty("grams_per_portion") public Double gramsPerPortion;

    /** main | sauce | seasoning | aroma */
    @JsonProperty("role")              public String role;

    public static PreparedDishIngredientRecord from(RecipeIngredientCandidate ing, int declaredServings) {
        PreparedDishIngredientRecord r = new PreparedDishIngredientRecord();
        r.creaId          = ing.creaCode != null ? "crea_" + ing.creaCode.strip() : null;
        r.nameRaw         = ing.ingredientNameRaw;
        r.gramsPerPortion = ing.gramsRaw != null
                ? round2(ing.gramsRaw / Math.max(1, declaredServings))
                : null;
        r.role            = ing.role;
        return r;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
