package it.dsms.grabber.engine.curated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecipeCandidate {

    @JsonProperty("candidate_id")   public String candidateId;
    @JsonProperty("target_id")      public String targetId;
    @JsonProperty("dish_name")      public String dishName;
    @JsonProperty("declared_servings") public int declaredServings = 1;
    @JsonProperty("computed_weight_g") public Double computedWeightG;
    @JsonProperty("kcal_per_100g")  public Double kcalPer100g;
    @JsonProperty("protein_per_100g") public Double proteinPer100g;
    @JsonProperty("carbs_per_100g") public Double carbsPer100g;
    @JsonProperty("fat_per_100g")   public Double fatPer100g;
    @JsonProperty("fiber_per_100g") public Double fiberPer100g;
    @JsonProperty("default_portion_g") public Double defaultPortionG;
    @JsonProperty("crea_coverage_pct") public Double creaCoveragePct;
    @JsonProperty("confidence_level")  public String confidenceLevel;
    @JsonProperty("source_ref")        public String sourceRef = "internal_curation_llm_assisted";
    @JsonProperty("extraction_method") public String extractionMethod = "llm_assisted";
    @JsonProperty("llm_model")         public String llmModel;
    @JsonProperty("llm_prompt_version") public String llmPromptVersion;
    @JsonProperty("review_status")     public String reviewStatus = "draft";
    @JsonProperty("quality_flags")     public String qualityFlags;
    @JsonProperty("ingredients")       public List<RecipeIngredientCandidate> ingredients = new ArrayList<>();
}
