package it.dsms.grabber.curated;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class CuratedRecipeTargetCreaRef {
    @JsonIgnore
    public String targetId;

    @JsonProperty("code")
    public String creaCode;

    @JsonIgnore
    public int refOrder;

    public String label;

    public String role;

    @JsonProperty("is_primary")
    public boolean primary;

    @JsonProperty("crea_name_it")
    public String creaNameIt;
}
