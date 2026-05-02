package it.dsms.grabber.curated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DishAmbiguityRule {

    @JsonProperty("rule_id")            public String ruleId;
    @JsonProperty("target_kind")        public String targetKind;
    @JsonProperty("target_value")       public String targetValue;
    @JsonProperty("threshold_ratio")    public double thresholdRatio;
    @JsonProperty("correction_factor")  public double correctionFactor;
    @JsonProperty("range_min_factor")   public double rangeMinFactor;
    @JsonProperty("range_max_factor")   public double rangeMaxFactor;
    @JsonProperty("confidence_penalty") public int    confidencePenalty;
    @JsonProperty("note_template")      public String noteTemplate;
    @JsonProperty("source_ref")         public String sourceRef;
}
