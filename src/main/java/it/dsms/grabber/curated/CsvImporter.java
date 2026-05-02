package it.dsms.grabber.curated;

import it.dsms.grabber.db.PostgresConnector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class CsvImporter {

    private final PostgresConnector db;

    public CsvImporter(PostgresConnector db) {
        this.db = db;
    }

    public void importRules(File csv) throws IOException, SQLException {
        List<DishAmbiguityRule> rules = parseRules(csv);
        int ok = 0;
        for (DishAmbiguityRule r : rules) {
            db.upsertRule(r);
            ok++;
            System.out.printf("  rule  %-45s  OK%n", r.ruleId);
        }
        System.out.printf("Importate %d dish_ambiguity_rules da %s%n", ok, csv.getName());
    }

    public void importCorrections(File csv) throws IOException, SQLException {
        List<MealContextCorrection> corrections = parseCorrections(csv);
        int ok = 0;
        for (MealContextCorrection c : corrections) {
            db.upsertCorrection(c);
            ok++;
            System.out.printf("  ctx   %-45s  OK%n", c.context);
        }
        System.out.printf("Importate %d meal_context_corrections da %s%n", ok, csv.getName());
    }

    // -------------------------------------------------------------------------
    // Parsers
    // -------------------------------------------------------------------------

    private List<DishAmbiguityRule> parseRules(File csv) throws IOException {
        List<DishAmbiguityRule> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {
            String header = br.readLine(); // skip header
            if (header == null) return result;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = parseLine(line);
                // rule_id,target_kind,target_value,threshold_ratio,correction_factor,
                // range_min_factor,range_max_factor,confidence_penalty,note_template,source_ref
                DishAmbiguityRule r = new DishAmbiguityRule();
                r.ruleId            = f[0];
                r.targetKind        = f[1];
                r.targetValue       = f[2];
                r.thresholdRatio    = Double.parseDouble(f[3]);
                r.correctionFactor  = Double.parseDouble(f[4]);
                r.rangeMinFactor    = Double.parseDouble(f[5]);
                r.rangeMaxFactor    = Double.parseDouble(f[6]);
                r.confidencePenalty = Integer.parseInt(f[7]);
                r.noteTemplate      = f[8];
                r.sourceRef         = f.length > 9 ? f[9] : null;
                result.add(r);
            }
        }
        return result;
    }

    private List<MealContextCorrection> parseCorrections(File csv) throws IOException {
        List<MealContextCorrection> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {
            String header = br.readLine(); // skip header
            if (header == null) return result;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = parseLine(line);
                // context,kcal_multiplier,flat_extra_per_meal,range_widen_pct,
                // confidence_penalty,note_template,source_ref
                MealContextCorrection c = new MealContextCorrection();
                c.context           = f[0];
                c.kcalMultiplier    = Double.parseDouble(f[1]);
                c.flatExtraPerMeal  = Double.parseDouble(f[2]);
                c.rangeWidenPct     = Double.parseDouble(f[3]);
                c.confidencePenalty = Integer.parseInt(f[4]);
                c.noteTemplate      = f[5];
                c.sourceRef         = f.length > 6 ? f[6] : null;
                result.add(c);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // CSV line parser — handles double-quoted fields containing commas
    // -------------------------------------------------------------------------

    static String[] parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
