package it.dsms.grabber.engine.curated;

import it.dsms.grabber.engine.db.PostgresConnector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public void importRecipeTargets(File csv) throws IOException, SQLException {
        List<CuratedRecipeTarget> targets = parseRecipeTargets(csv);
        int ok = 0;
        for (CuratedRecipeTarget t : targets) {
            db.upsertRecipeTarget(t);
            ok++;
            System.out.printf("  target %-45s  OK%n", t.targetId);
        }
        System.out.printf("Importati %d curated_recipe_targets da %s%n", ok, csv.getName());
    }

    public void importRecipeTargetRefs(File csv) throws IOException, SQLException {
        Map<String, List<CuratedRecipeTargetCreaRef>> refsByTarget = parseRecipeTargetRefs(csv);
        int targets = 0;
        int refs = 0;
        for (Map.Entry<String, List<CuratedRecipeTargetCreaRef>> entry : refsByTarget.entrySet()) {
            db.replaceRecipeTargetRefs(entry.getKey(), entry.getValue());
            targets++;
            refs += entry.getValue().size();
            System.out.printf("  refs   %-45s  %d%n", entry.getKey(), entry.getValue().size());
        }
        System.out.printf("Importati %d curated_recipe_target_crea_refs per %d target da %s%n",
                refs, targets, csv.getName());
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

    private List<CuratedRecipeTarget> parseRecipeTargets(File csv) throws IOException {
        List<CuratedRecipeTarget> result = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {
            String header = br.readLine(); // skip header
            if (header == null) return result;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = parseLine(line);
                // target_id,dish_query,display_name,meal_area,priority,
                // crea_feasibility,notes,source_file,source_line,review_status
                CuratedRecipeTarget t = new CuratedRecipeTarget();
                t.targetId        = f[0];
                t.dishQuery       = f[1];
                t.displayName     = f[2];
                t.mealArea        = f[3];
                t.priority        = parseNullableInt(f[4]);
                t.creaFeasibility = f[5];
                t.notes           = f[6];
                t.sourceFile      = f[7];
                t.sourceLine      = parseNullableInt(f[8]);
                t.reviewStatus    = f.length > 9 && !f[9].isBlank() ? f[9] : "pending";
                result.add(t);
            }
        }
        return result;
    }

    private Map<String, List<CuratedRecipeTargetCreaRef>> parseRecipeTargetRefs(File csv) throws IOException {
        Map<String, List<CuratedRecipeTargetCreaRef>> result = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(csv), StandardCharsets.UTF_8))) {
            String header = br.readLine(); // skip header
            if (header == null) return result;
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = parseLine(line);
                // target_id,crea_code,ref_order,label,role,is_primary
                CuratedRecipeTargetCreaRef ref = new CuratedRecipeTargetCreaRef();
                ref.targetId = f[0];
                ref.creaCode = f[1];
                ref.refOrder = Integer.parseInt(f[2]);
                ref.label    = f.length > 3 ? blankToNull(f[3]) : null;
                ref.role     = f.length > 4 ? blankToNull(f[4]) : null;
                ref.primary  = f.length > 5 && Boolean.parseBoolean(f[5]);
                result.computeIfAbsent(ref.targetId, ignored -> new ArrayList<>()).add(ref);
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

    private Integer parseNullableInt(String value) {
        if (value == null || value.isBlank()) return null;
        return Integer.parseInt(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
