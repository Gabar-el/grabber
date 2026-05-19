package it.dsms.grabber.engine.export;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Struttura radice del file prepared_dishes_seed.json.
 *
 * Formato:
 * {
 *   "seed_version": "prepared_dishes_2026_05",
 *   "exported_at":  "2026-05-18T10:30:00Z",
 *   "count":        10,
 *   "items": [ { "id": "dish_91aa7095...", "name_it": "...", ... }, ... ]
 * }
 *
 * Letto in Flutter da PreparedDishSeedLoader tramite rootBundle.loadString().
 * seed_version usa lo stesso pattern di crea_seed.json: "prepared_dishes_YYYY_MM"
 * → usato da IsarPreparedDishService per il re-seed incrementale.
 */
public class PreparedDishSeedManifest {

    private static final DateTimeFormatter VERSION_FMT =
            DateTimeFormatter.ofPattern("yyyy_MM").withZone(ZoneOffset.UTC);

    @JsonProperty("seed_version")
    public String seedVersion = "prepared_dishes_" + VERSION_FMT.format(Instant.now());

    @JsonProperty("exported_at")
    public String exportedAt = Instant.now().toString();

    @JsonProperty("count")
    public int count;

    @JsonProperty("items")
    public List<PreparedDishRecord> items;
}
