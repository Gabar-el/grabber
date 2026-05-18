package it.dsms.grabber.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.dsms.grabber.curated.RecipeCandidate;

import java.util.List;

/**
 * Rappresentazione di un piatto preparato nel prepared_dishes_seed.json.
 *
 * Convenzioni:
 * - id: "dish_" + candidate_id senza prefisso "crc_"
 *   → garantisce unicità rispetto agli alimenti CREA ("crea_XXXXXX")
 * - kcal_per_portion: calcolato in export (non nel DB) come
 *   kcal_per_100g * default_portion_g / 100
 * - meal_area: caricato dal target associato (primo, secondo, contorno, ...)
 *   → null se il candidato non ha target_id
 * - Gli ingredienti sono per porzione (grams_raw / declared_servings)
 *
 * Campi di processo omessi (non servono a Flutter):
 *   candidate_id, declared_servings, computed_weight_g, crea_coverage_pct,
 *   confidence_level, llm_model, llm_prompt_version, review_status,
 *   quality_flags, source_ref, extraction_method.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PreparedDishRecord {

    @JsonProperty("id")               public String id;
    @JsonProperty("name_it")          public String nameIt;
    @JsonProperty("meal_area")        public String mealArea;
    @JsonProperty("kcal_per_100g")    public Double kcalPer100g;
    @JsonProperty("protein_per_100g") public Double proteinPer100g;
    @JsonProperty("carbs_per_100g")   public Double carbsPer100g;
    @JsonProperty("fat_per_100g")     public Double fatPer100g;
    @JsonProperty("fiber_per_100g")   public Double fiberPer100g;
    @JsonProperty("default_portion_g") public Double defaultPortionG;

    /**
     * Kcal effettive per la porzione di default.
     * Calcolato: kcal_per_100g * default_portion_g / 100.
     * Campo derivato — più utile all'utente finale di kcal_per_100g da sola.
     */
    @JsonProperty("kcal_per_portion") public Double kcalPerPortion;

    @JsonProperty("ingredients")      public List<PreparedDishIngredientRecord> ingredients;

    public static PreparedDishRecord from(RecipeCandidate c, String mealArea,
                                          List<PreparedDishIngredientRecord> ingredients) {
        PreparedDishRecord r = new PreparedDishRecord();

        // "dish_" + parte esadecimale del candidate_id (tolto prefisso "crc_")
        String hexPart = c.candidateId.startsWith("crc_")
                ? c.candidateId.substring(4)
                : c.candidateId;
        r.id             = "dish_" + hexPart;
        r.nameIt         = c.dishName;
        r.mealArea       = mealArea;
        r.kcalPer100g    = c.kcalPer100g;
        r.proteinPer100g = c.proteinPer100g;
        r.carbsPer100g   = c.carbsPer100g;
        r.fatPer100g     = c.fatPer100g;
        r.fiberPer100g   = c.fiberPer100g;
        r.defaultPortionG = c.defaultPortionG;

        if (c.kcalPer100g != null && c.defaultPortionG != null) {
            r.kcalPerPortion = round2(c.kcalPer100g * c.defaultPortionG / 100.0);
        }

        r.ingredients = ingredients;
        return r;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
