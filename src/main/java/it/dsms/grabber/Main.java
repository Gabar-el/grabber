package it.dsms.grabber;

import it.dsms.grabber.crea.CreaDetailScraper;
import it.dsms.grabber.crea.CreaFood;
import it.dsms.grabber.crea.CreaIdFetcher;
import it.dsms.grabber.curated.ApproveRecipeCommand;
import it.dsms.grabber.curated.CuratedRecipeTarget;
import it.dsms.grabber.curated.CuratedRecipeTargetReviewPromoter;
import it.dsms.grabber.curated.CurateRecipeCommand;
import it.dsms.grabber.curated.CsvImporter;
import it.dsms.grabber.curated.ExportRecipeSeedCommand;
import it.dsms.grabber.db.PostgresConnector;
import it.dsms.grabber.export.DsmsExporter;

import java.sql.SQLException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Punto di ingresso.
 *
 * Comandi:
 *   grab                    — scarica tutti gli alimenti CREA e li salva nel DB (default)
 *   export [file]           — esporta crea_foods in crea_seed.json (Flutter asset)
 *   grab-export [file]      — grab + export in sequenza
 *   import-curated [dir]    — legge CSV da data/curated/ (o [dir]) e upserta nel DB
 *   export-all [dir]        — esporta i tre JSON seed in [dir] (default: cartella corrente)
 *   curate-recipe <dish>    — genera candidato ricetta via LLM e salva nel DB
 *   approve-recipe <id>     — promuove un candidato a approved
 *   reject-recipe  <id>     — segna un candidato come rejected
 *   export-seed [--out f]   — esporta i candidati approved in prepared_dishes_seed.json
 *
 * Esempi:
 *   java -jar grabber.jar
 *   java -jar grabber.jar grab
 *   java -jar grabber.jar export
 *   java -jar grabber.jar export C:\percorso\dsms\assets\seeds\crea_seed.json
 *   java -jar grabber.jar grab-export C:\percorso\dsms\assets\seeds\crea_seed.json
 *   java -jar grabber.jar import-curated
 *   java -jar grabber.jar import-curated C:\percorso\grabber\data\curated
 *   java -jar grabber.jar export-all C:\percorso\dsms\assets\seeds
 */
public class Main {

    private static final String CREA_BASE_URL =
            "https://www.alimentinutrizione.it/tabelle-nutrizionali/";

    private static final String DEFAULT_CURATED_DIR = "data/curated";
    private static final String DEFAULT_RECIPE_TARGET_VALIDATOR_LOG =
            "artifacts/curated_recipe_targets_validator.log";

    private static final int RATE_LIMIT_MS = 1_000;   // 1 req/sec verso CREA
    private static final int MAX_RETRIES   = 3;
    private static final int RETRY_WAIT_MS = 5_000;

    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        String command    = args.length > 0 ? args[0] : "grab";
        String outputPath = args.length > 1 ? args[1] : "crea_seed.json";

        switch (command) {
            case "grab"            -> runGrab();
            case "export"          -> runExport(outputPath);
            case "grab-export"     -> { runGrab(); runExport(outputPath); }
            case "import-curated"  -> runImportCurated(outputPath);
            case "export-recipe-targets" -> runExportRecipeTargets(
                    outputPath.equals("crea_seed.json") ? "curated_recipe_targets_initial.json" : outputPath,
                    args.length > 2 && "--approved-only".equals(args[2])
            );
            case "review-recipe-targets" -> runReviewRecipeTargets(args);
            case "export-all"      -> runExportAll(outputPath);
            case "curate-recipe"   -> runCurateRecipe(args);
            case "approve-recipe"  -> runApproveRecipe(args);
            case "reject-recipe"   -> runRejectRecipe(args);
            case "export-seed"     -> runExportSeed(args);
            default                -> {
                System.err.println("Comando non riconosciuto: " + command);
                System.err.println("Uso: grabber [grab|export|grab-export|import-curated|export-recipe-targets|review-recipe-targets|export-all|curate-recipe|approve-recipe|reject-recipe|export-seed] [path]");
                System.exit(1);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Grab
    // -------------------------------------------------------------------------

    private static void runGrab() throws Exception {
        System.out.println("=== DSMS Grabber — modalità grab ===");

        // Fase 1: recupera tutti i codici per categoria
        System.out.println("\n[1/2] Recupero lista codici CREA per categoria...");
        CreaIdFetcher fetcher = new CreaIdFetcher();
        List<String> codes = fetcher.fetchAll();
        System.out.printf("      Totale codici unici: %d%n", codes.size());

        if (codes.isEmpty()) {
            System.err.println("Nessun codice recuperato. Verifica la connessione o le categorie.");
            return;
        }

        // Fase 2: scraping dettaglio + upsert
        System.out.println("\n[2/2] Scraping pagine di dettaglio...");
        CreaDetailScraper scraper = new CreaDetailScraper();
        int ok = 0, err = 0;

        try (DbHandle db = new DbHandle()) {
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                String prefix = String.format("[%3d/%3d] %s", i + 1, codes.size(), code);

                try {
                    Document doc = fetchWithRetry(code);
                    CreaFood food = scraper.parse(code, doc);
                    db.connector.upsert(food);
                    ok++;
                    System.out.printf("%s  OK   %s%n", prefix, food.nameIt != null ? food.nameIt : "(nome non trovato)");
                } catch (Exception e) {
                    err++;
                    System.err.printf("%s  ERR  %s%n", prefix, e.getMessage());
                }

                if (i < codes.size() - 1) Thread.sleep(RATE_LIMIT_MS);
            }
        }

        System.out.printf("%nCompletato: %d OK — %d errori su %d totali.%n", ok, err, codes.size());
    }

    /**
     * Effettua il fetch della pagina con retry su errori transienti (5xx, timeout).
     */
    private static Document fetchWithRetry(String code) throws Exception {
        String url = CREA_BASE_URL + code;
        Exception last = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (compatible; DSMSGrabber/1.0)")
                        .timeout(15_000)
                        .get();
            } catch (Exception e) {
                last = e;
                if (attempt < MAX_RETRIES) {
                    System.err.printf("         retry %d/%d per %s (%s)%n",
                            attempt, MAX_RETRIES, code, e.getMessage());
                    Thread.sleep(RETRY_WAIT_MS);
                }
            }
        }
        throw last;
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    private static void runExport(String outputPath) throws Exception {
        System.out.println("=== DSMS Grabber — modalità export ===");
        File outputFile = new File(outputPath);
        try (DbHandle db = new DbHandle()) {
            new DsmsExporter(db.connector).export(outputFile);
        }
    }

    // -------------------------------------------------------------------------
    // Import curated CSV
    // -------------------------------------------------------------------------

    private static void runImportCurated(String dirPath) throws Exception {
        System.out.println("=== DSMS Grabber — modalità import-curated ===");
        File dir = new File(dirPath.equals("crea_seed.json") ? DEFAULT_CURATED_DIR : dirPath);
        File rulesFile       = new File(dir, "dish_ambiguity_rules.csv");
        File correctionsFile = new File(dir, "meal_context_corrections.csv");
        File recipeTargetsFile = new File(dir, "curated_recipe_targets.csv");
        File recipeTargetRefsFile = new File(dir, "curated_recipe_target_crea_refs.csv");

        if (!rulesFile.exists()) {
            System.err.println("File non trovato: " + rulesFile.getAbsolutePath());
            System.exit(1);
        }
        if (!correctionsFile.exists()) {
            System.err.println("File non trovato: " + correctionsFile.getAbsolutePath());
            System.exit(1);
        }

        try (DbHandle db = new DbHandle()) {
            CsvImporter importer = new CsvImporter(db.connector);
            System.out.println("\n[1/2] dish_ambiguity_rules...");
            importer.importRules(rulesFile);
            System.out.println("\n[2/2] meal_context_corrections...");
            importer.importCorrections(correctionsFile);
            if (recipeTargetsFile.exists()) {
                System.out.println("\n[3/4] curated_recipe_targets...");
                importer.importRecipeTargets(recipeTargetsFile);
            }
            if (recipeTargetRefsFile.exists()) {
                System.out.println("\n[4/4] curated_recipe_target_crea_refs...");
                importer.importRecipeTargetRefs(recipeTargetRefsFile);
            }
        }
        System.out.println("\nImport completato.");
    }

    private static void runExportRecipeTargets(String outputPath, boolean approvedOnly) throws Exception {
        if ("-".equals(outputPath)) {
            System.err.println("=== DSMS Grabber - modalita export-recipe-targets ===");
        } else {
            System.out.println("=== DSMS Grabber - modalita export-recipe-targets ===");
        }
        File outputFile = new File(outputPath);
        try (DbHandle db = new DbHandle()) {
            new DsmsExporter(db.connector).exportRecipeTargets(outputFile, !approvedOnly);
        }
    }

    private static void runReviewRecipeTargets(String[] args) throws Exception {
        System.out.println("=== DSMS Grabber - modalita review-recipe-targets ===");

        boolean apply = false;
        String logPath = DEFAULT_RECIPE_TARGET_VALIDATOR_LOG;
        for (int i = 1; i < args.length; i++) {
            if ("--apply".equals(args[i])) {
                apply = true;
            } else {
                logPath = args[i];
            }
        }

        File validatorLog = new File(logPath);
        CuratedRecipeTargetReviewPromoter promoter = new CuratedRecipeTargetReviewPromoter();
        try (DbHandle db = new DbHandle()) {
            List<CuratedRecipeTarget> targets = db.connector.findRecipeTargets(true);
            CuratedRecipeTargetReviewPromoter.ReviewPlan plan =
                    promoter.buildPlan(targets, validatorLog);

            System.out.println("Log validator: " + validatorLog.getAbsolutePath());
            System.out.println("Target totali: " + plan.totalTargets);
            System.out.println("Target citati da warning: " + plan.warningTargetIds.size());
            System.out.println("Stato attuale: " + formatCounts(plan.currentStatusCounts));
            System.out.println("Stato pianificato: " + formatCounts(plan.plannedStatusCounts));
            System.out.println("Cambi di stato: " + plan.statusChanges.size());
            if (!plan.unknownWarningTargetIds.isEmpty()) {
                System.out.println("Warning con target_id non presenti in DB: " + plan.unknownWarningTargetIds);
            }
            if (!plan.warningTargetIds.isEmpty()) {
                System.out.println("Primi target in needs_review: " + plan.firstWarningTargetIds(20));
            }

            if (apply) {
                db.connector.updateRecipeTargetReviewStatuses(plan.statusChanges);
                System.out.println("Aggiornamento applicato.");
            } else {
                System.out.println("Dry-run: nessuna modifica al DB. Usa --apply per applicare.");
            }
        }
    }

    private static String formatCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("(vuoto)");
    }

    // -------------------------------------------------------------------------
    // Export all seeds
    // -------------------------------------------------------------------------

    private static void runExportAll(String dirPath) throws Exception {
        System.out.println("=== DSMS Grabber — modalità export-all ===");
        File dir = new File(dirPath.equals("crea_seed.json") ? "." : dirPath);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("Impossibile creare la directory: " + dir.getAbsolutePath());
            System.exit(1);
        }

        try (DbHandle db = new DbHandle()) {
            DsmsExporter exporter = new DsmsExporter(db.connector);
            System.out.println("\n[1/4] crea_seed.json...");
            exporter.export(new File(dir, "crea_seed.json"));
            System.out.println("\n[2/4] dish_ambiguity_rules.json...");
            exporter.exportRules(new File(dir, "dish_ambiguity_rules.json"));
            System.out.println("\n[3/4] meal_context_corrections.json...");
            exporter.exportCorrections(new File(dir, "meal_context_corrections.json"));
            System.out.println("\n[4/4] prepared_dishes_seed.json...");
            new ExportRecipeSeedCommand(db.connector).execute(new File(dir, "prepared_dishes_seed.json").getAbsolutePath());
        }
        System.out.println("\nExport completato.");
    }

    // -------------------------------------------------------------------------
    // Curate recipe
    // -------------------------------------------------------------------------

    private static void runCurateRecipe(String[] args) throws Exception {
        if (args.length < 2 || args[1].startsWith("--")) {
            System.err.println("Uso: curate-recipe <dish_name> [--target-id <id>] [--servings <n>] [--model <model_name>]");
            System.err.println("Esempio: curate-recipe \"pasta al pomodoro\" --servings 2");
            System.exit(1);
        }

        String dishName = args[1];
        String targetId = null;
        int servings    = 4;  // default: 4 porzioni — grammature più naturali per l'LLM, default_portion_g = total/4
        String model    = null;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--target-id" -> {
                    if (i + 1 < args.length) targetId = args[++i];
                }
                case "--servings" -> {
                    if (i + 1 < args.length) {
                        try { servings = Integer.parseInt(args[++i]); }
                        catch (NumberFormatException e) {
                            System.err.println("--servings deve essere un numero intero. Usato default: 1");
                        }
                    }
                }
                case "--model" -> {
                    if (i + 1 < args.length) model = args[++i];
                }
                default -> System.err.println("Opzione non riconosciuta: " + args[i] + " (ignorata)");
            }
        }

        try (DbHandle db = new DbHandle()) {
            new CurateRecipeCommand(db.connector).execute(dishName, targetId, servings, model);
        }
    }

    // -------------------------------------------------------------------------
    // Approve / Reject recipe
    // -------------------------------------------------------------------------

    private static void runApproveRecipe(String[] args) throws Exception {
        if (args.length < 2 || args[1].startsWith("--")) {
            System.err.println("Uso: approve-recipe <candidate_id>");
            System.err.println("Esempio: approve-recipe crc_91aa7095f9d34959");
            System.exit(1);
        }
        String candidateId = args[1];
        try (DbHandle db = new DbHandle()) {
            new ApproveRecipeCommand(db.connector).approve(candidateId);
        }
    }

    private static void runRejectRecipe(String[] args) throws Exception {
        if (args.length < 2 || args[1].startsWith("--")) {
            System.err.println("Uso: reject-recipe <candidate_id> [nota]");
            System.err.println("Esempio: reject-recipe crc_91aa7095f9d34959 \"grammature irrealistiche\"");
            System.exit(1);
        }
        String candidateId = args[1];
        String note = args.length > 2 ? args[2] : null;
        try (DbHandle db = new DbHandle()) {
            new ApproveRecipeCommand(db.connector).reject(candidateId, note);
        }
    }

    private static void runExportSeed(String[] args) throws Exception {
        String outputPath = "prepared_dishes_seed.json";
        for (int i = 1; i < args.length; i++) {
            if ("--out".equals(args[i]) && i + 1 < args.length) {
                outputPath = args[++i];
            }
        }
        try (DbHandle db = new DbHandle()) {
            new ExportRecipeSeedCommand(db.connector).execute(outputPath);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** AutoCloseable wrapper per PostgresConnector (try-with-resources). */
    private static class DbHandle implements AutoCloseable {
        final PostgresConnector connector;

        DbHandle() throws SQLException {
            this.connector = new PostgresConnector();
        }

        @Override
        public void close() throws Exception {
            connector.close();
        }
    }
}
