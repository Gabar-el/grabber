package it.dsms.grabber.engine.validation;

import it.dsms.grabber.engine.curated.CuratedRecipeTarget;
import it.dsms.grabber.engine.curated.CuratedRecipeTargetCreaRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CuratedRecipeTargetsValidator {

    private static final Map<Character, String> MEAL_AREA_BY_PREFIX = Map.of(
            'A', "antipasto",
            'P', "primo",
            'S', "secondo",
            'C', "contorno",
            'N', "spuntino"
    );

    public ValidationResult validate(List<CuratedRecipeTarget> targets) {
        ValidationResult result = new ValidationResult();
        Map<String, String> dishQueryIndex = new HashMap<>();

        if (targets.isEmpty()) {
            result.errors.add("Nessun curated_recipe_target da esportare.");
            return result;
        }

        for (CuratedRecipeTarget target : targets) {
            validateTarget(target, result);

            String normalizedQuery = target.dishQuery == null
                    ? ""
                    : target.dishQuery.trim().toLowerCase(Locale.ROOT);
            if (!normalizedQuery.isEmpty()) {
                String previous = dishQueryIndex.putIfAbsent(normalizedQuery, target.targetId);
                if (previous != null && !previous.equals(target.targetId)) {
                    result.warnings.add("Dish query duplicata: '" + target.dishQuery +
                            "' in " + previous + " e " + target.targetId);
                }
            }
        }
        return result;
    }

    private void validateTarget(CuratedRecipeTarget target, ValidationResult result) {
        if (isBlank(target.targetId)) {
            result.errors.add("Target senza target_id.");
            return;
        }
        if (!target.targetId.matches("[APSCN][0-9]{2,3}")) {
            result.errors.add(target.targetId + ": target_id non conforme al pattern [APSCN][0-9]{2,3}.");
        }

        char prefix = target.targetId.charAt(0);
        String expectedMealArea = MEAL_AREA_BY_PREFIX.get(prefix);
        if (expectedMealArea == null || !expectedMealArea.equals(target.mealArea)) {
            result.errors.add(target.targetId + ": meal_area '" + target.mealArea +
                    "' non coerente con prefisso " + prefix + ".");
        }

        if (isBlank(target.dishQuery)) {
            result.errors.add(target.targetId + ": dish_query mancante.");
        }
        if (isBlank(target.displayName)) {
            result.errors.add(target.targetId + ": display_name mancante.");
        }
        if (isBlank(target.creaFeasibility)) {
            result.errors.add(target.targetId + ": crea_feasibility mancante.");
        }
        if ("medium".equals(target.creaFeasibility) && isBlank(target.notes)) {
            result.errors.add(target.targetId + ": crea_feasibility medium richiede una nota.");
        }
        if (target.creaRefs == null || target.creaRefs.isEmpty()) {
            result.errors.add(target.targetId + ": nessun riferimento CREA.");
            return;
        }

        int primaryCount = 0;
        int expectedOrder = 1;
        for (CuratedRecipeTargetCreaRef ref : target.creaRefs) {
            if (isBlank(ref.creaCode)) {
                result.errors.add(target.targetId + ": ref CREA senza codice.");
            }
            if (ref.refOrder != expectedOrder) {
                result.errors.add(target.targetId + ": ref_order non continuo, atteso " +
                        expectedOrder + " trovato " + ref.refOrder + ".");
            }
            if (ref.primary) primaryCount++;
            expectedOrder++;
        }
        if (primaryCount != 1) {
            result.errors.add(target.targetId + ": deve avere esattamente un ref primario, trovati " + primaryCount + ".");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class ValidationResult {
        public final List<String> errors = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();

        public boolean ok() {
            return errors.isEmpty();
        }
    }
}
