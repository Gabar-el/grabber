package it.dsms.grabber;

import it.dsms.grabber.crea.CreaDetailScraper;
import it.dsms.grabber.crea.CreaFood;
import it.dsms.grabber.crea.CreaIdFetcher;
import it.dsms.grabber.curated.CsvImporter;
import it.dsms.grabber.db.PostgresConnector;
import it.dsms.grabber.export.DsmsExporter;

import java.sql.SQLException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.util.List;

/**
 * Punto di ingresso.
 *
 * Comandi:
 *   grab                    — scarica tutti gli alimenti CREA e li salva nel DB (default)
 *   export [file]           — esporta crea_foods in crea_seed.json (Flutter asset)
 *   grab-export [file]      — grab + export in sequenza
 *   import-curated [dir]    — legge CSV da data/curated/ (o [dir]) e upserta nel DB
 *   export-all [dir]        — esporta i tre JSON seed in [dir] (default: cartella corrente)
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
            case "export-all"      -> runExportAll(outputPath);
            default                -> {
                System.err.println("Comando non riconosciuto: " + command);
                System.err.println("Uso: grabber [grab|export|grab-export|import-curated|export-all] [path]");
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
        }
        System.out.println("\nImport completato.");
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
            System.out.println("\n[1/3] crea_seed.json...");
            exporter.export(new File(dir, "crea_seed.json"));
            System.out.println("\n[2/3] dish_ambiguity_rules.json...");
            exporter.exportRules(new File(dir, "dish_ambiguity_rules.json"));
            System.out.println("\n[3/3] meal_context_corrections.json...");
            exporter.exportCorrections(new File(dir, "meal_context_corrections.json"));
        }
        System.out.println("\nExport completato.");
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
