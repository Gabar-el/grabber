package it.dsms.grabber.export;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Struttura radice del file crea_seed.json.
 *
 * Formato:
 * {
 *   "seed_version": "crea_2026_04",
 *   "exported_at":  "2026-04-18T10:30:00Z",
 *   "count":        950,
 *   "items": [ { "id": "crea_005000", "name_it": "Aglio", ... }, ... ]
 * }
 *
 * Letto in Flutter da CreaFoodSeedLoader tramite rootBundle.loadString().
 * I campi snake_case corrispondono direttamente ai nomi attesi dal loader Dart.
 *
 * seed_version: "crea_YYYY_MM" — usato da IsarFoodCatalogService
 * per il re-seed incrementale (confronto versione salvata vs versione asset).
 */
public class DsmsExportManifest {

    private static final DateTimeFormatter VERSION_FMT =
            DateTimeFormatter.ofPattern("yyyy_MM").withZone(ZoneOffset.UTC);

    @JsonProperty("seed_version") public String          seedVersion = "crea_" + VERSION_FMT.format(Instant.now());
    @JsonProperty("exported_at")  public String          exportedAt  = Instant.now().toString();
    @JsonProperty("count")        public int             count;
    @JsonProperty("items")        public List<DsmsRecord> items;
}
