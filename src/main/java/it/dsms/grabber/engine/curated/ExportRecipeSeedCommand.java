package it.dsms.grabber.engine.curated;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.dsms.grabber.engine.db.PostgresConnector;
import it.dsms.grabber.engine.export.PreparedDishIngredientRecord;
import it.dsms.grabber.engine.export.PreparedDishRecord;
import it.dsms.grabber.engine.export.PreparedDishSeedManifest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Comando export-seed: esporta tutti i recipe_candidates approvati
 * in prepared_dishes_seed.json, pronto per essere copiato in Flutter.
 *
 * Uso:
 *   export-seed [--out <file>]
 *
 * Argomenti:
 *   --out <file>  — path di output (default: prepared_dishes_seed.json)
 *                   Usare "-" per stdout.
 *
 * Esempi:
 *   java -jar grabber.jar export-seed
 *   java -jar grabber.jar export-seed --out C:\...\dsms\assets\seeds\prepared_dishes_seed.json
 *   java -jar grabber.jar export-seed --out -
 */
public class ExportRecipeSeedCommand {

    private final PostgresConnector db;

    public ExportRecipeSeedCommand(PostgresConnector db) {
        this.db = db;
    }

    // -------------------------------------------------------------------------

    public void execute(String outputPath) throws SQLException, IOException {

        System.out.println("=== export-seed: prepared_dishes_seed.json ===");

        // 1. Carica tutti i candidati approvati con i loro ingredienti
        System.out.println("[1/3] Caricamento candidati approved dal DB...");
        List<RecipeCandidate> candidates = db.findApprovedCandidatesWithIngredients();
        System.out.println("      " + candidates.size() + " candidati trovati.");

        if (candidates.isEmpty()) {
            System.err.println("WARN: nessun candidato con review_status='approved'.");
            System.err.println("      Approva almeno un candidato con: approve-recipe <candidate_id>");
            return;
        }

        // 2. Carica i meal_area dai target (per arricchire il record del piatto)
        System.out.println("[2/3] Caricamento meal_area dai target...");
        Map<String, String> mealAreaByTargetId = db.findMealAreaByTargetId();

        // 3. Costruisce il manifest
        System.out.println("[3/3] Costruzione manifest e serializzazione...");
        List<PreparedDishRecord> items = candidates.stream()
                .map(c -> {
                    String mealArea = c.targetId != null
                            ? mealAreaByTargetId.get(c.targetId)
                            : null;
                    List<PreparedDishIngredientRecord> ingRecords = c.ingredients.stream()
                            .map(ing -> PreparedDishIngredientRecord.from(ing, c.declaredServings))
                            .collect(Collectors.toList());
                    return PreparedDishRecord.from(c, mealArea, ingRecords);
                })
                .collect(Collectors.toList());

        PreparedDishSeedManifest manifest = new PreparedDishSeedManifest();
        manifest.count = items.size();
        manifest.items = items;

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String json = mapper.writeValueAsString(manifest);

        if ("-".equals(outputPath)) {
            System.out.println(json);
            System.err.printf("%n--- export-seed: %d piatti → stdout ---%n", manifest.count);
            System.err.printf("seed_version: %s%n", manifest.seedVersion);
        } else {
            File out = new File(outputPath);
            File parent = out.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Impossibile creare la directory: " + parent.getAbsolutePath());
            }
            Files.writeString(out.toPath(), json, StandardCharsets.UTF_8);
            System.out.printf("%n--- export-seed completato ---%n");
            System.out.printf("  piatti esportati : %d%n", manifest.count);
            System.out.printf("  seed_version     : %s%n", manifest.seedVersion);
            System.out.printf("  file             : %s%n", out.getAbsolutePath());
        }

        // Stampa riepilogo piatti esportati
        System.out.println();
        System.out.println("Piatti nel seed:");
        items.forEach(d -> System.out.printf(
                "  %-45s  %6.1f kcal/100g  %5.0fg/porz  %6.1f kcal/porz%n",
                d.nameIt,
                d.kcalPer100g   != null ? d.kcalPer100g   : 0.0,
                d.defaultPortionG != null ? d.defaultPortionG : 0.0,
                d.kcalPerPortion  != null ? d.kcalPerPortion  : 0.0
        ));
    }
}
