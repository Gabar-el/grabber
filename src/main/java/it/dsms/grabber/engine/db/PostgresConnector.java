package it.dsms.grabber.engine.db;

import it.dsms.grabber.engine.crea.CreaFood;
import it.dsms.grabber.engine.curated.CuratedRecipeTarget;
import it.dsms.grabber.engine.curated.CuratedRecipeTargetCreaRef;
import it.dsms.grabber.engine.curated.DishAmbiguityRule;
import it.dsms.grabber.engine.curated.MealContextCorrection;
import it.dsms.grabber.engine.curated.RecipeCandidate;
import it.dsms.grabber.engine.curated.RecipeIngredientCandidate;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    public void upsertRecipeTarget(CuratedRecipeTarget t) throws SQLException {
        String sql = """
                INSERT INTO curated_recipe_targets
                    (target_id, dish_query, display_name, meal_area, priority,
                     crea_feasibility, notes, source_file, source_line, review_status)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (target_id) DO UPDATE SET
                    dish_query       = EXCLUDED.dish_query,
                    display_name     = EXCLUDED.display_name,
                    meal_area        = EXCLUDED.meal_area,
                    priority         = EXCLUDED.priority,
                    crea_feasibility = EXCLUDED.crea_feasibility,
                    notes            = EXCLUDED.notes,
                    source_file      = EXCLUDED.source_file,
                    source_line      = EXCLUDED.source_line,
                    review_status    = EXCLUDED.review_status,
                    updated_at       = now()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, t.targetId);
            ps.setString(2, t.dishQuery);
            ps.setString(3, t.displayName);
            ps.setString(4, t.mealArea);
            setNullableInteger(ps, 5, t.priority);
            ps.setString(6, t.creaFeasibility);
            ps.setString(7, t.notes);
            ps.setString(8, t.sourceFile);
            setNullableInteger(ps, 9, t.sourceLine);
            ps.setString(10, t.reviewStatus != null ? t.reviewStatus : "pending");
            ps.executeUpdate();
        }
    }

    public void replaceRecipeTargetRefs(String targetId, List<CuratedRecipeTargetCreaRef> refs) throws SQLException {
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM curated_recipe_target_crea_refs WHERE target_id = ?")) {
                delete.setString(1, targetId);
                delete.executeUpdate();
            }

            String sql = """
                    INSERT INTO curated_recipe_target_crea_refs
                        (target_id, crea_code, ref_order, label, role, is_primary)
                    VALUES (?,?,?,?,?,?)
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (CuratedRecipeTargetCreaRef ref : refs) {
                    ps.setString(1, targetId);
                    ps.setString(2, ref.creaCode);
                    ps.setInt(3, ref.refOrder);
                    ps.setString(4, ref.label);
                    ps.setString(5, ref.role);
                    ps.setBoolean(6, ref.primary);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public void updateRecipeTargetReviewStatuses(Map<String, String> statusesByTargetId) throws SQLException {
        if (statusesByTargetId.isEmpty()) return;

        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            String sql = """
                    UPDATE curated_recipe_targets
                    SET review_status = ?, updated_at = now()
                    WHERE target_id = ?
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (Map.Entry<String, String> entry : statusesByTargetId.entrySet()) {
                    ps.setString(1, entry.getValue());
                    ps.setString(2, entry.getKey());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
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

    public List<CuratedRecipeTarget> findRecipeTargets(boolean includePending) throws SQLException {
        String sql = """
                SELECT target_id, dish_query, display_name, meal_area, priority,
                       crea_feasibility, notes, source_file, source_line, review_status
                FROM curated_recipe_targets
                WHERE (? OR review_status = 'approved')
                ORDER BY meal_area, priority NULLS LAST, target_id
                """;
        Map<String, CuratedRecipeTarget> targets = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, includePending);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CuratedRecipeTarget t = new CuratedRecipeTarget();
                    t.targetId        = rs.getString("target_id");
                    t.dishQuery       = rs.getString("dish_query");
                    t.displayName     = rs.getString("display_name");
                    t.mealArea        = rs.getString("meal_area");
                    t.priority        = nullableInteger(rs, "priority");
                    t.creaFeasibility = rs.getString("crea_feasibility");
                    t.notes           = rs.getString("notes");
                    t.sourceFile      = rs.getString("source_file");
                    t.sourceLine      = nullableInteger(rs, "source_line");
                    t.reviewStatus    = rs.getString("review_status");
                    targets.put(t.targetId, t);
                }
            }
        }

        if (targets.isEmpty()) return new ArrayList<>();

        String refsSql = """
                SELECT r.target_id, r.crea_code, r.ref_order, r.label, r.role,
                       r.is_primary, c.name_it AS crea_name_it
                FROM curated_recipe_target_crea_refs r
                JOIN crea_foods c ON c.code = r.crea_code
                ORDER BY r.target_id, r.ref_order
                """;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(refsSql)) {
            while (rs.next()) {
                String targetId = rs.getString("target_id");
                CuratedRecipeTarget target = targets.get(targetId);
                if (target == null) continue;

                CuratedRecipeTargetCreaRef ref = new CuratedRecipeTargetCreaRef();
                ref.targetId    = targetId;
                ref.creaCode    = rs.getString("crea_code").trim();
                ref.refOrder    = rs.getInt("ref_order");
                ref.label       = rs.getString("label");
                ref.role        = rs.getString("role");
                ref.primary     = rs.getBoolean("is_primary");
                ref.creaNameIt  = rs.getString("crea_name_it");
                target.creaRefs.add(ref);
            }
        }
        return new ArrayList<>(targets.values());
    }

    /**
     * Trova un singolo target per ID (con i suoi crea_refs).
     */
    public CuratedRecipeTarget findRecipeTargetById(String targetId) throws SQLException {
        String sql = """
                SELECT target_id, dish_query, display_name, meal_area, priority,
                       crea_feasibility, notes, source_file, source_line, review_status
                FROM curated_recipe_targets
                WHERE target_id = ?
                LIMIT 1
                """;
        CuratedRecipeTarget target = null;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, targetId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    target = new CuratedRecipeTarget();
                    target.targetId        = rs.getString("target_id");
                    target.dishQuery       = rs.getString("dish_query");
                    target.displayName     = rs.getString("display_name");
                    target.mealArea        = rs.getString("meal_area");
                    target.priority        = nullableInteger(rs, "priority");
                    target.creaFeasibility = rs.getString("crea_feasibility");
                    target.notes           = rs.getString("notes");
                    target.sourceFile      = rs.getString("source_file");
                    target.sourceLine      = nullableInteger(rs, "source_line");
                    target.reviewStatus    = rs.getString("review_status");
                }
            }
        }
        if (target == null) return null;

        String refsSql = """
                SELECT r.target_id, r.crea_code, r.ref_order, r.label, r.role,
                       r.is_primary, c.name_it AS crea_name_it
                FROM curated_recipe_target_crea_refs r
                JOIN crea_foods c ON c.code = r.crea_code
                WHERE r.target_id = ?
                ORDER BY r.ref_order
                """;
        try (PreparedStatement ps = connection.prepareStatement(refsSql)) {
            ps.setString(1, targetId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CuratedRecipeTargetCreaRef ref = new CuratedRecipeTargetCreaRef();
                    ref.targetId   = rs.getString("target_id");
                    ref.creaCode   = rs.getString("crea_code").trim();
                    ref.refOrder   = rs.getInt("ref_order");
                    ref.label      = rs.getString("label");
                    ref.role       = rs.getString("role");
                    ref.primary    = rs.getBoolean("is_primary");
                    ref.creaNameIt = rs.getString("crea_name_it");
                    target.creaRefs.add(ref);
                }
            }
        }
        return target;
    }

    /**
     * Inserisce o aggiorna un RecipeCandidate (upsert su candidate_id).
     */
    public void upsertRecipeCandidate(RecipeCandidate c) throws SQLException {
        String sql = """
                INSERT INTO recipe_candidates
                    (candidate_id, target_id, dish_name, declared_servings,
                     computed_weight_g, kcal_per_100g, protein_per_100g, carbs_per_100g,
                     fat_per_100g, fiber_per_100g, default_portion_g, crea_coverage_pct,
                     confidence_level, source_ref, extraction_method,
                     llm_model, llm_prompt_version, review_status, quality_flags)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (candidate_id) DO UPDATE SET
                    target_id          = EXCLUDED.target_id,
                    dish_name          = EXCLUDED.dish_name,
                    declared_servings  = EXCLUDED.declared_servings,
                    computed_weight_g  = EXCLUDED.computed_weight_g,
                    kcal_per_100g      = EXCLUDED.kcal_per_100g,
                    protein_per_100g   = EXCLUDED.protein_per_100g,
                    carbs_per_100g     = EXCLUDED.carbs_per_100g,
                    fat_per_100g       = EXCLUDED.fat_per_100g,
                    fiber_per_100g     = EXCLUDED.fiber_per_100g,
                    default_portion_g  = EXCLUDED.default_portion_g,
                    crea_coverage_pct  = EXCLUDED.crea_coverage_pct,
                    confidence_level   = EXCLUDED.confidence_level,
                    source_ref         = EXCLUDED.source_ref,
                    extraction_method  = EXCLUDED.extraction_method,
                    llm_model          = EXCLUDED.llm_model,
                    llm_prompt_version = EXCLUDED.llm_prompt_version,
                    review_status      = EXCLUDED.review_status,
                    quality_flags      = EXCLUDED.quality_flags,
                    updated_at         = now()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1,  c.candidateId);
            if (c.targetId != null) ps.setString(2, c.targetId);
            else                    ps.setNull(2, Types.VARCHAR);
            ps.setString(3,  c.dishName);
            ps.setInt(4,     c.declaredServings);
            setNullableDouble(ps, 5,  c.computedWeightG);
            setNullableDouble(ps, 6,  c.kcalPer100g);
            setNullableDouble(ps, 7,  c.proteinPer100g);
            setNullableDouble(ps, 8,  c.carbsPer100g);
            setNullableDouble(ps, 9,  c.fatPer100g);
            setNullableDouble(ps, 10, c.fiberPer100g);
            setNullableDouble(ps, 11, c.defaultPortionG);
            setNullableDouble(ps, 12, c.creaCoveragePct);
            if (c.confidenceLevel != null) ps.setString(13, c.confidenceLevel);
            else                           ps.setNull(13, Types.VARCHAR);
            ps.setString(14, c.sourceRef != null ? c.sourceRef : "internal_curation_llm_assisted");
            ps.setString(15, c.extractionMethod != null ? c.extractionMethod : "llm_assisted");
            if (c.llmModel != null) ps.setString(16, c.llmModel);
            else                    ps.setNull(16, Types.VARCHAR);
            if (c.llmPromptVersion != null) ps.setString(17, c.llmPromptVersion);
            else                            ps.setNull(17, Types.VARCHAR);
            ps.setString(18, c.reviewStatus != null ? c.reviewStatus : "draft");
            if (c.qualityFlags != null) ps.setString(19, c.qualityFlags);
            else                        ps.setNull(19, Types.VARCHAR);
            ps.executeUpdate();
        }
    }

    /**
     * Sostituisce gli ingredienti per un candidato (DELETE + INSERT).
     */
    public void replaceRecipeIngredients(String candidateId, List<RecipeIngredientCandidate> ingredients)
            throws SQLException {
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM recipe_ingredient_candidates WHERE candidate_id = ?")) {
                delete.setString(1, candidateId);
                delete.executeUpdate();
            }

            String sql = """
                    INSERT INTO recipe_ingredient_candidates
                        (candidate_id, crea_code, ingredient_name_raw, grams_raw,
                         grams_normalized, yield_factor, weight_contribution_g,
                         kcal_contribution, match_method, match_confidence,
                         role, sort_order)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                    """;
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                for (RecipeIngredientCandidate ing : ingredients) {
                    ps.setString(1, candidateId);
                    if (ing.creaCode != null) ps.setString(2, ing.creaCode);
                    else                      ps.setNull(2, Types.VARCHAR);
                    ps.setString(3, ing.ingredientNameRaw);
                    setNullableDouble(ps, 4, ing.gramsRaw);
                    setNullableDouble(ps, 5, ing.gramsNormalized);
                    ps.setDouble(6, ing.yieldFactor);
                    setNullableDouble(ps, 7, ing.weightContributionG);
                    setNullableDouble(ps, 8, ing.kcalContribution);
                    if (ing.matchMethod != null) ps.setString(9, ing.matchMethod);
                    else                         ps.setNull(9, Types.VARCHAR);
                    setNullableDouble(ps, 10, ing.matchConfidence);
                    ps.setString(11, ing.role != null ? ing.role : "main");
                    ps.setInt(12, ing.sortOrder);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    /**
     * Legge il summary di un candidato (senza ingredienti) per candidate_id.
     * Ritorna null se non trovato.
     */
    public RecipeCandidate findRecipeCandidateSummary(String candidateId) throws SQLException {
        String sql = """
                SELECT candidate_id, target_id, dish_name, declared_servings,
                       computed_weight_g, kcal_per_100g, protein_per_100g, carbs_per_100g,
                       fat_per_100g, fiber_per_100g, default_portion_g, crea_coverage_pct,
                       confidence_level, llm_model, llm_prompt_version,
                       review_status, quality_flags
                FROM recipe_candidates
                WHERE candidate_id = ?
                LIMIT 1
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, candidateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                RecipeCandidate c = new RecipeCandidate();
                c.candidateId      = rs.getString("candidate_id");
                c.targetId         = rs.getString("target_id");
                c.dishName         = rs.getString("dish_name");
                c.declaredServings = rs.getInt("declared_servings");
                c.computedWeightG  = nullableDouble(rs, "computed_weight_g");
                c.kcalPer100g      = nullableDouble(rs, "kcal_per_100g");
                c.proteinPer100g   = nullableDouble(rs, "protein_per_100g");
                c.carbsPer100g     = nullableDouble(rs, "carbs_per_100g");
                c.fatPer100g       = nullableDouble(rs, "fat_per_100g");
                c.fiberPer100g     = nullableDouble(rs, "fiber_per_100g");
                c.defaultPortionG  = nullableDouble(rs, "default_portion_g");
                c.creaCoveragePct  = nullableDouble(rs, "crea_coverage_pct");
                c.confidenceLevel  = rs.getString("confidence_level");
                c.llmModel         = rs.getString("llm_model");
                c.llmPromptVersion = rs.getString("llm_prompt_version");
                c.reviewStatus     = rs.getString("review_status");
                c.qualityFlags     = rs.getString("quality_flags");
                return c;
            }
        }
    }

    /**
     * Restituisce tutti i recipe_candidates con review_status='approved',
     * ciascuno completo di lista ingredienti.
     * Ordinamento: dish_name ASC.
     */
    public List<RecipeCandidate> findApprovedCandidatesWithIngredients() throws SQLException {
        String sql = """
                SELECT candidate_id, target_id, dish_name, declared_servings,
                       computed_weight_g, kcal_per_100g, protein_per_100g, carbs_per_100g,
                       fat_per_100g, fiber_per_100g, default_portion_g,
                       crea_coverage_pct, confidence_level, source_ref, extraction_method,
                       llm_model, llm_prompt_version, review_status, quality_flags
                FROM recipe_candidates
                WHERE review_status = 'approved'
                ORDER BY dish_name
                """;
        List<RecipeCandidate> candidates = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                RecipeCandidate c = new RecipeCandidate();
                c.candidateId      = rs.getString("candidate_id");
                c.targetId         = rs.getString("target_id");
                c.dishName         = rs.getString("dish_name");
                c.declaredServings = rs.getInt("declared_servings");
                c.computedWeightG  = nullableDouble(rs, "computed_weight_g");
                c.kcalPer100g      = nullableDouble(rs, "kcal_per_100g");
                c.proteinPer100g   = nullableDouble(rs, "protein_per_100g");
                c.carbsPer100g     = nullableDouble(rs, "carbs_per_100g");
                c.fatPer100g       = nullableDouble(rs, "fat_per_100g");
                c.fiberPer100g     = nullableDouble(rs, "fiber_per_100g");
                c.defaultPortionG  = nullableDouble(rs, "default_portion_g");
                c.creaCoveragePct  = nullableDouble(rs, "crea_coverage_pct");
                c.confidenceLevel  = rs.getString("confidence_level");
                c.sourceRef        = rs.getString("source_ref");
                c.extractionMethod = rs.getString("extraction_method");
                c.llmModel         = rs.getString("llm_model");
                c.llmPromptVersion = rs.getString("llm_prompt_version");
                c.reviewStatus     = rs.getString("review_status");
                c.qualityFlags     = rs.getString("quality_flags");
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) return candidates;

        // Carica gli ingredienti per tutti i candidati in una sola query
        String ids = candidates.stream()
                .map(c -> "'" + c.candidateId.replace("'", "''") + "'")
                .collect(java.util.stream.Collectors.joining(","));
        String ingsSql = """
                SELECT i.candidate_id, i.crea_code, i.ingredient_name_raw,
                       i.grams_raw, i.grams_normalized, i.yield_factor,
                       i.weight_contribution_g, i.kcal_contribution,
                       i.match_method, i.match_confidence, i.role, i.sort_order
                FROM recipe_ingredient_candidates i
                WHERE i.candidate_id IN (""" + ids + """
                )
                ORDER BY i.candidate_id, i.sort_order
                """;
        Map<String, RecipeCandidate> byId = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(c -> c.candidateId, c -> c));
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(ingsSql)) {
            while (rs.next()) {
                String cid = rs.getString("candidate_id");
                RecipeCandidate owner = byId.get(cid);
                if (owner == null) continue;
                RecipeIngredientCandidate ing = new RecipeIngredientCandidate();
                ing.candidateId         = cid;
                ing.creaCode            = rs.getString("crea_code");
                ing.ingredientNameRaw   = rs.getString("ingredient_name_raw");
                ing.gramsRaw            = nullableDouble(rs, "grams_raw");
                ing.gramsNormalized     = nullableDouble(rs, "grams_normalized");
                ing.yieldFactor         = rs.getDouble("yield_factor");
                ing.weightContributionG = nullableDouble(rs, "weight_contribution_g");
                ing.kcalContribution    = nullableDouble(rs, "kcal_contribution");
                ing.matchMethod         = rs.getString("match_method");
                ing.matchConfidence     = nullableDouble(rs, "match_confidence");
                ing.role                = rs.getString("role");
                ing.sortOrder           = rs.getInt("sort_order");
                owner.ingredients.add(ing);
            }
        }
        return candidates;
    }

    /**
     * Restituisce una mappa target_id -> meal_area per tutti i target.
     * Usato dall'export-seed per arricchire ogni piatto con il suo meal_area.
     */
    public Map<String, String> findMealAreaByTargetId() throws SQLException {
        String sql = "SELECT target_id, meal_area FROM curated_recipe_targets";
        Map<String, String> result = new java.util.HashMap<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("target_id"), rs.getString("meal_area"));
            }
        }
        return result;
    }

    /**
     * Aggiorna il review_status di un candidato.
     * Ritorna true se almeno una riga e' stata aggiornata.
     */
    public boolean setRecipeCandidateStatus(String candidateId, String newStatus) throws SQLException {
        String sql = """
                UPDATE recipe_candidates
                SET review_status = ?, updated_at = now()
                WHERE candidate_id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newStatus);
            ps.setString(2, candidateId);
            return ps.executeUpdate() > 0;
        }
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

    private void setNullableInteger(PreparedStatement ps, int idx, Integer value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.INTEGER);
        else               ps.setInt(idx, value);
    }

    private Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private Integer nullableInteger(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
