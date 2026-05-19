package it.dsms.grabber.engine.curated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

public class YieldFactorTable {

    private static final String DEFAULT_PATH = "assets/seeds/yield_factors.json";
    private static final double DEFAULT_FACTOR = 1.0;

    private final Map<String, Double> byCreaCode;
    private final double defaultFactor;

    public YieldFactorTable() {
        this(DEFAULT_PATH);
    }

    public YieldFactorTable(String path) {
        Map<String, Double> loaded = Collections.emptyMap();
        double def = DEFAULT_FACTOR;
        try {
            File f = new File(path);
            if (f.exists()) {
                ObjectMapper mapper = new ObjectMapper();
                RawTable raw = mapper.readValue(f, RawTable.class);
                if (raw.byCreaCode != null) loaded = raw.byCreaCode;
                if (raw.defaultYieldFactor != null) def = raw.defaultYieldFactor;
            } else {
                System.err.println("WARN yield_factors.json non trovato a: " + path + " — uso default 1.0");
            }
        } catch (IOException e) {
            System.err.println("WARN errore lettura yield_factors.json: " + e.getMessage());
        }
        this.byCreaCode    = loaded;
        this.defaultFactor = def;
    }

    public double getYieldFactor(String creaCode) {
        if (creaCode == null) return defaultFactor;
        return byCreaCode.getOrDefault(creaCode.trim(), defaultFactor);
    }

    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class RawTable {
        @JsonProperty("default_yield_factor") public Double defaultYieldFactor;
        @JsonProperty("by_crea_code")         public Map<String, Double> byCreaCode;
    }
}
