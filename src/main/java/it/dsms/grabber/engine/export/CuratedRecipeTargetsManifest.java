package it.dsms.grabber.engine.export;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.dsms.grabber.engine.curated.CuratedRecipeTarget;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CuratedRecipeTargetsManifest {
    private static final DateTimeFormatter VERSION_FMT =
            DateTimeFormatter.ofPattern("yyyy_MM").withZone(ZoneOffset.UTC);

    @JsonProperty("seed_version")
    public String seedVersion = "curated_recipe_targets_" + VERSION_FMT.format(Instant.now());

    @JsonProperty("exported_at")
    public String exportedAt = Instant.now().toString();

    public int count;

    public List<CuratedRecipeTarget> targets;
}
