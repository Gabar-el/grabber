package it.dsms.grabber.engine.curated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecipeIngredientCandidate {

    @JsonProperty("candidate_id")          public String candidateId;
    @JsonProperty("crea_code")             public String creaCode;
    @JsonProperty("crea_name_it")          public String creaNameIt;
    @JsonProperty("ingredient_name_raw")   public String ingredientNameRaw;
    @JsonProperty("grams_raw")             public Double gramsRaw;
    @JsonProperty("grams_normalized")      public Double gramsNormalized;
    @JsonProperty("yield_factor")          public double yieldFactor = 1.0;
    @JsonProperty("weight_contribution_g") public Double weightContributionG;
    @JsonProperty("kcal_contribution")     public Double kcalContribution;
    @JsonProperty("match_method")          public String matchMethod;
    @JsonProperty("match_confidence")      public Double matchConfidence;
    @JsonProperty("role")                  public String role = "main";
    @JsonProperty("sort_order")            public int sortOrder;
}
