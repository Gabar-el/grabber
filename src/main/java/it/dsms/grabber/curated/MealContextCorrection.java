package it.dsms.grabber.curated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MealContextCorrection {

    @JsonProperty("context")              public String context;
    @JsonProperty("kcal_multiplier")      public double kcalMultiplier;
    @JsonProperty("flat_extra_per_meal")  public double flatExtraPerMeal;
    @JsonProperty("range_widen_pct")      public double rangeWidenPct;
    @JsonProperty("confidence_penalty")   public int    confidencePenalty;
    @JsonProperty("note_template")        public String noteTemplate;
    @JsonProperty("source_ref")           public String sourceRef;
}
