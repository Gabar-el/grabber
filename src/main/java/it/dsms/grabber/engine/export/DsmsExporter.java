package it.dsms.grabber.engine.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.dsms.grabber.engine.crea.CreaFood;
import it.dsms.grabber.engine.curated.CuratedRecipeTarget;
import it.dsms.grabber.engine.curated.DishAmbiguityRule;
import it.dsms.grabber.engine.curated.MealContextCorrection;
import it.dsms.grabber.engine.db.PostgresConnector;
import it.dsms.grabber.engine.validation.CuratedRecipeTargetsValidator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;

/**
 * Legge tutti i record da crea_foods e li serializza in un file .dsms.
 *
 * Uso da Main:
 *   DsmsExporter exporter = new DsmsExporter(connector);
 *   exporter.export(new File("crea_export.dsms"));
 */
public class DsmsExporter {

    private final PostgresConnector db;
    private final ObjectMapper mapper;

    public DsmsExporter(PostgresConnector db) {
        this.db = db;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void export(File outputFile) throws SQLException, IOException {
        List<CreaFood> foods = db.findAllCrea();

        List<DsmsRecord> records = foods.stream()
                .map(DsmsRecord::from)
                .toList();

        DsmsExportManifest manifest = new DsmsExportManifest();
        manifest.count = records.size();
        manifest.items = records;

        mapper.writeValue(outputFile, manifest);

        System.out.printf("Esportati %d alimenti → %s%n", manifest.count, outputFile.getAbsolutePath());
        System.out.printf("seed_version: %s%n", manifest.seedVersion);
    }

    public void exportRules(File outputFile) throws SQLException, IOException {
        List<DishAmbiguityRule> rules = db.findAllRules();
        mapper.writeValue(outputFile, rules);
        System.out.printf("Esportate %d dish_ambiguity_rules → %s%n", rules.size(), outputFile.getAbsolutePath());
    }

    public void exportCorrections(File outputFile) throws SQLException, IOException {
        List<MealContextCorrection> corrections = db.findAllCorrections();
        mapper.writeValue(outputFile, corrections);
        System.out.printf("Esportate %d meal_context_corrections → %s%n", corrections.size(), outputFile.getAbsolutePath());
    }

    public void exportRecipeTargets(File outputFile, boolean includePending) throws SQLException, IOException {
        List<CuratedRecipeTarget> targets = db.findRecipeTargets(includePending);

        CuratedRecipeTargetsValidator validator = new CuratedRecipeTargetsValidator();
        CuratedRecipeTargetsValidator.ValidationResult validation = validator.validate(targets);
        for (String warning : validation.warnings) {
            System.err.println("WARN recipe-targets: " + warning);
        }
        if (!validation.ok()) {
            for (String error : validation.errors) {
                System.err.println("ERR recipe-targets: " + error);
            }
            throw new IllegalStateException("Export recipe targets bloccato: " +
                    validation.errors.size() + " errori di validazione.");
        }

        CuratedRecipeTargetsManifest manifest = new CuratedRecipeTargetsManifest();
        manifest.count = targets.size();
        manifest.targets = targets;

        String json = mapper.writeValueAsString(manifest);
        if ("-".equals(outputFile.getPath())) {
            System.out.print(json);
            System.err.printf("%nEsportati %d curated_recipe_targets -> stdout%n", manifest.count);
            System.err.printf("seed_version: %s%n", manifest.seedVersion);
        } else {
            Files.writeString(outputFile.toPath(), json, StandardCharsets.UTF_8);
            System.out.printf("Esportati %d curated_recipe_targets -> %s%n", manifest.count, outputFile.getAbsolutePath());
            System.out.printf("seed_version: %s%n", manifest.seedVersion);
        }
    }
}
