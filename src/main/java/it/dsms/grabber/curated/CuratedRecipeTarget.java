package it.dsms.grabber.curated;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class CuratedRecipeTarget {
    @JsonProperty("target_id")
    public String targetId;

    @JsonProperty("dish_query")
    public String dishQuery;

    @JsonProperty("display_name")
    public String displayName;

    @JsonProperty("meal_area")
    public String mealArea;

    public Integer priority;

    @JsonProperty("crea_feasibility")
    public String creaFeasibility;

    public String notes;

    @JsonProperty("source_file")
    public String sourceFile;

    @JsonProperty("source_line")
    public Integer sourceLine;

    @JsonProperty("review_status")
    public String reviewStatus = "pending";

    @JsonProperty("crea_refs")
    public List<CuratedRecipeTargetCreaRef> creaRefs = new ArrayList<>();
}
