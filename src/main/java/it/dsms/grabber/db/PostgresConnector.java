package it.dsms.grabber.db;

import it.dsms.grabber.crea.CreaFood;
import it.dsms.grabber.curated.DishAmbiguityRule;
import it.dsms.grabber.curated.MealContextCorrection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostgresConnector {

    private static final String URL  = "jdbc:postgresql://localhost:5432/dsms_grabber";
    private static final String USER = "grabber";
    private static final String PASS = "grabber_pw";

    private final Connection connection;

    public PostgresConnector() throws SQLException {
        this.connection = DriverManager.getConnection(URL, USER, PASS);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void upsert(CreaFood food) throws SQLException {
        String sql = """
                INSERT INTO crea_foods
                    (code, name_it, name_en, name_sci, category,
                     kcal, protein_g, carbs_g, fat_g, fiber_g, water_g, portion_g)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (code) DO UPDATE SET
                    name_it   = EXCLUDED.name_it,
                    name_en   = EXCLUDED.name_en,
                    name_sci  = EXCLUDED.name_sci,
                    category  = EXCLUDED.category,
                    kcal      = EXCLUDED.kcal,
                    protein_g = EXCLUDED.protein_g,
                    carbs_g   = EXCLUDED.carbs_g,
                    fat_g     = EXCLUDED.fat_g,
                    fiber_g   = EXCLUDED.fiber_g,
                    water_g   = EXCLUDED.water_g,
                    portion_g = EXCLUDED.portion_g,
                    scraped_at = now()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, food.code);
            ps.setString(2, food.nameIt);
            ps.setString(3, food.nameEn);
            ps.setString(4, food.nameSci);
            ps.setString(5, food.category);
            setNullableDouble(ps, 6,  food.kcal);
            setNullableDouble(ps, 7,  food.proteinG);
            setNullableDouble(ps, 8,  food.carbsG);
            setNullableDouble(ps, 9,  food.fatG);
            setNullableDouble(ps, 10, food.fiberG);
            setNullableDouble(ps, 11, food.waterG);
            setNullableDouble(ps, 12, food.portionG);
            ps.executeUpdate();
        }
    }

    public void upsertRule(DishAmbiguityRule r) throws SQLException {
        String sql = """
                INSERT INTO dish_ambiguity_rules
                    (rule_id, target_kind, target_value, threshold_ratio,
                     correction_factor, range_min_factor, range_max_factor,
                     confidence_penalty, note_template, source_ref)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (rule_id) DO UPDATE SET
                    target_kind        = EXCLUDED.target_kind,
                    target_value       = EXCLUDED.target_value,
                    threshold_ratio    = EXCLUDED.threshold_ratio,
                    correction_factor  = EXCLUDED.correction_factor,
                    range_min_factor   = EXCLUDED.range_min_factor,
                    range_max_factor   = EXCLUDED.range_max_factor,
                    confidence_penalty = EXCLUDED.confidence_penalty,
                    note_template      = EXCLUDED.note_template,
                    source_ref         = EXCLUDED.source_ref,
                    curated_at         = now()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, r.ruleId);
            ps.setString(2, r.targetKind);
            ps.setString(3, r.targetValue);
            ps.setDouble(4, r.thresholdRatio);
            ps.setDouble(5, r.correctionFactor);
            ps.setDouble(6, r.rangeMinFactor);
            ps.setDouble(7, r.rangeMaxFactor);
            ps.setInt(8,    r.confidencePenalty);
            ps.setString(9, r.noteTemplate);
            ps.setString(10, r.sourceRef);
            ps.executeUpdate();
        }
    }

    public void upsertCorrection(MealContextCorrection c) throws SQLException {
        String sql = """
                INSERT INTO meal_context_corrections
                    (context, kcal_multiplier, flat_extra_per_meal, range_widen_pct,
                     confidence_penalty, note_template, source_ref)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT (context) DO UPDATE SET
                    kcal_multiplier     = EXCLUDED.kcal_multiplier,
                    flat_extra_per_meal = EXCLUDED.flat_extra_per_meal,
                    range_widen_pct     = EXCLUDED.range_widen_pct,
                    confidence_penalty  = EXCLUDED.confidence_penalty,
                    note_template       = EXCLUDED.note_template,
                    source_ref          = EXCLUDED.source_ref,
                    curated_at          = now()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, c.context);
            ps.setDouble(2, c.kcalMultiplier);
            ps.setDouble(3, c.flatExtraPerMeal);
            ps.setDouble(4, c.rangeWidenPct);
            ps.setInt(5,    c.confidencePenalty);
            ps.setString(6, c.noteTemplate);
            ps.setString(7, c.sourceRef);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<CreaFood> findAllCrea() throws SQLException {
        String sql = """
                SELECT code, name_it, name_en, name_sci, category,
                       kcal, protein_g, carbs_g, fat_g, fiber_g, water_g, portion_g
                FROM crea_foods
                ORDER BY code
                """;
        List<CreaFood> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs  = st.executeQuery(sql)) {
            while (rs.next()) {
                CreaFood f = new CreaFood();
                f.code      = rs.getString("code");
                f.nameIt    = rs.getString("name_it");
                f.nameEn    = rs.getString("name_en");
                f.nameSci   = rs.getString("name_sci");
                f.category  = rs.getString("category");
                f.kcal      = nullableDouble(rs, "kcal");
                f.proteinG  = nullableDouble(rs, "protein_g");
                f.carbsG    = nullableDouble(rs, "carbs_g");
                f.fatG      = nullableDouble(rs, "fat_g");
                f.fiberG    = nullableDouble(rs, "fiber_g");
                f.waterG    = nullableDouble(rs, "water_g");
                f.portionG  = nullableDouble(rs, "portion_g");
                result.add(f);
            }
        }
        return result;
    }

    public List<DishAmbiguityRule> findAllRules() throws SQLException {
        String sql = "SELECT rule_id, target_kind, target_value, threshold_ratio, " +
                     "correction_factor, range_min_factor, range_max_factor, " +
                     "confidence_penalty, note_template, source_ref " +
                     "FROM dish_ambiguity_rules ORDER BY rule_id";
        List<DishAmbiguityRule> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                DishAmbiguityRule r = new DishAmbiguityRule();
                r.ruleId            = rs.getString("rule_id");
                r.targetKind        = rs.getString("target_kind");
                r.targetValue       = rs.getString("target_value");
                r.thresholdRatio    = rs.getDouble("threshold_ratio");
                r.correctionFactor  = rs.getDouble("correction_factor");
                r.rangeMinFactor    = rs.getDouble("range_min_factor");
                r.rangeMaxFactor    = rs.getDouble("range_max_factor");
                r.confidencePenalty = rs.getInt("confidence_penalty");
                r.noteTemplate      = rs.getString("note_template");
                r.sourceRef         = rs.getString("source_ref");
                result.add(r);
            }
        }
        return result;
    }

    public List<MealContextCorrection> findAllCorrections() throws SQLException {
        String sql = "SELECT context, kcal_multiplier, flat_extra_per_meal, range_widen_pct, " +
                     "confidence_penalty, note_template, source_ref " +
                     "FROM meal_context_corrections ORDER BY context";
        List<MealContextCorrection> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                MealContextCorrection c = new MealContextCorrection();
                c.context           = rs.getString("context");
                c.kcalMultiplier    = rs.getDouble("kcal_multiplier");
                c.flatExtraPerMeal  = rs.getDouble("flat_extra_per_meal");
                c.rangeWidenPct     = rs.getDouble("range_widen_pct");
                c.confidencePenalty = rs.getInt("confidence_penalty");
                c.noteTemplate      = rs.getString("note_template");
                c.sourceRef         = rs.getString("source_ref");
                result.add(c);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.NUMERIC);
        else               ps.setDouble(idx, value);
    }

    private Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
