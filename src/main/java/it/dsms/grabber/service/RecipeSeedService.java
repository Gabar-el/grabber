package it.dsms.grabber.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.dsms.grabber.engine.export.PreparedDishIngredientRecord;
import it.dsms.grabber.engine.export.PreparedDishRecord;
import it.dsms.grabber.engine.export.PreparedDishSeedManifest;
import it.dsms.grabber.entity.RecipeCandidateEntity;
import it.dsms.grabber.entity.RecipeIngredientCandidateEntity;
import it.dsms.grabber.repository.RecipeCandidateRepository;
import it.dsms.grabber.repository.RecipeIngredientCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Servizio per l'esportazione del seed prepared_dishes_seed.json.
 *
 * <p>Equivalente Spring del comando CLI {@code export-seed}.
 * Usato dall'endpoint {@code GET /api/seed/export}.
 *
 * <p>Evita N+1 caricando tutti i candidati approved e tutti i loro
 * ingredienti in 2 query, poi unendoli in memoria.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeSeedService {

    private static final DateTimeFormatter VERSION_FMT =
            DateTimeFormatter.ofPattern("yyyy_MM").withZone(ZoneOffset.UTC);

    private final RecipeCandidateRepository           candidateRepository;
    private final RecipeIngredientCandidateRepository ingredientRepository;
    private final ObjectMapper                         objectMapper;

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    /**
     * Serializza tutti i candidati approved in JSON (bytes UTF-8).
     * Pronto per essere restituito come body di un endpoint HTTP download.
     *
     * @return JSON bytes del seed
     * @throws IOException in caso di errore di serializzazione
     */
    public byte[] exportSeedJson() throws IOException {
        List<RecipeCandidateEntity> approved = candidateRepository.findAllApproved();
        log.info("exportSeedJson: {} candidati approved trovati", approved.size());

        // Carica tutti gli ingredienti in una query sola, poi raggruppa per candidate_id
        List<RecipeIngredientCandidateEntity> allIngredients =
                ingredientRepository.findAllByApprovedCandidates();

        Map<String, List<RecipeIngredientCandidateEntity>> byCandidate = allIngredients.stream()
                .collect(Collectors.groupingBy(i -> i.getCandidate().getCandidateId()));

        // Costruisce i PreparedDishRecord
        List<PreparedDishRecord> items = approved.stream()
                .map(c -> toPreparedDishRecord(c, byCandidate.getOrDefault(c.getCandidateId(), List.of())))
                .collect(Collectors.toList());

        // Manifesto
        PreparedDishSeedManifest manifest = new PreparedDishSeedManifest();
        manifest.seedVersion = "prepared_dishes_" + VERSION_FMT.format(Instant.now());
        manifest.exportedAt  = Instant.now().toString();
        manifest.count       = items.size();
        manifest.items       = items;

        ObjectMapper pretty = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);
        return pretty.writeValueAsBytes(manifest);
    }

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    /**
     * DTO per l'endpoint {@code GET /api/seed/status}.
     */
    public record SeedStatusDto(long nApproved, String seedVersion) {}

    public SeedStatusDto getSeedStatus() {
        long n = candidateRepository.countApproved();
        String version = "prepared_dishes_" + VERSION_FMT.format(Instant.now());
        return new SeedStatusDto(n, version);
    }

    // -------------------------------------------------------------------------
    // Conversione entity → record export
    // -------------------------------------------------------------------------

    private PreparedDishRecord toPreparedDishRecord(RecipeCandidateEntity c,
                                                     List<RecipeIngredientCandidateEntity> ingredients) {
        PreparedDishRecord r = new PreparedDishRecord();

        // id: "dish_" + parte esadecimale dopo "crc_"
        r.id = "dish_" + c.getCandidateId().replace("crc_", "");
        r.nameIt = c.getDishName();

        // meal_area dal target associato
        r.mealArea = (c.getTarget() != null) ? c.getTarget().getMealArea() : null;

        // macros
        r.kcalPer100g    = toDouble(c.getKcalPer100g());
        r.proteinPer100g = toDouble(c.getProteinPer100g());
        r.carbsPer100g   = toDouble(c.getCarbsPer100g());
        r.fatPer100g     = toDouble(c.getFatPer100g());
        r.fiberPer100g   = toDouble(c.getFiberPer100g());
        r.defaultPortionG = toDouble(c.getDefaultPortionG());

        // kcal_per_portion = kcal_per_100g * default_portion_g / 100
        if (r.kcalPer100g != null && r.defaultPortionG != null) {
            r.kcalPerPortion = round2(r.kcalPer100g * r.defaultPortionG / 100.0);
        }

        // ingredienti
        r.ingredients = ingredients.stream()
                .map(i -> toIngredientRecord(i, c.getDeclaredServings()))
                .collect(Collectors.toList());

        return r;
    }

    private PreparedDishIngredientRecord toIngredientRecord(RecipeIngredientCandidateEntity i,
                                                             int declaredServings) {
        PreparedDishIngredientRecord rec = new PreparedDishIngredientRecord();
        rec.creaId = i.getCreaCode() != null ? "crea_" + i.getCreaCode() : null;
        rec.nameRaw = i.getIngredientNameRaw();
        // gramsPerPortion = grams_raw / declared_servings
        if (i.getGramsRaw() != null && declaredServings > 0) {
            rec.gramsPerPortion = round2(toDouble(i.getGramsRaw()) / declaredServings);
        }
        rec.role = i.getRole();
        return rec;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static Double toDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : null;
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
