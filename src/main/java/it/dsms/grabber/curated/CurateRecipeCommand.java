package it.dsms.grabber.curated;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.dsms.grabber.crea.CreaFood;
import it.dsms.grabber.db.PostgresConnector;
import it.dsms.grabber.llm.AnthropicClient;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comando curate-recipe: genera un candidato ricetta tramite LLM e lo salva nel DB.
 *
 * Uso:
 *   curate-recipe <dish_name> [--target-id <id>] [--servings <n>] [--model <model>]
 */
public class CurateRecipeCommand {

    private static final int LLM_MAX_TOKENS = 1500;
    private static final String ARTIFACTS_DIR = "artifacts/review";

    private final PostgresConnector db;

    public CurateRecipeCommand(PostgresConnector db) {
        this.db = db;
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public void execute(String dishName, String targetId, int servings, String model) throws Exception {
        System.out.println("=== curate-recipe: " + dishName + " ===");

        // 1. Carica CREA foods
        System.out.println("[1/6] Caricamento alimenti CREA dal DB...");
        List<CreaFood> creaFoods = db.findAllCrea();
        System.out.println("      " + creaFoods.size() + " alimenti caricati.");

        // 2. Carica target e refs (opzionale)
        CuratedRecipeTarget target = null;
        List<CuratedRecipeTargetCreaRef> hintRefs = new ArrayList<>();
        if (targetId != null && !targetId.isBlank()) {
            System.out.println("[2/6] Caricamento target: " + targetId);
            target = db.findRecipeTargetById(targetId);
            if (target == null) {
                System.err.println("WARN: target_id '" + targetId + "' non trovato nel DB. Procedo senza hint refs.");
            } else {
                hintRefs = target.creaRefs;
                System.out.println("      " + hintRefs.size() + " hint refs trovati.");
            }
        } else {
            System.out.println("[2/6] Nessun --target-id specificato, procedo senza hint refs.");
        }

        // 3. Carica yield factors
        System.out.println("[3/6] Caricamento yield factor table...");
        YieldFactorTable yieldTable = new YieldFactorTable();

        // 4. Chiama LLM
        System.out.println("[4/6] Chiamata LLM per ricetta '" + dishName + "' (" + servings + " porzioni)...");
        String llmResponseText;
        String llmModel;
        try {
            AnthropicClient client = new AnthropicClient(model);
            llmModel = client.getModel();
            String prompt = buildPrompt(dishName, servings, hintRefs);
            llmResponseText = client.complete(prompt, LLM_MAX_TOKENS);
            System.out.println("      Risposta LLM ricevuta (" + llmResponseText.length() + " chars).");
        } catch (IllegalStateException e) {
            System.err.println("ERRORE: " + e.getMessage());
            throw e;
        }

        // 5. Parsifica risposta LLM
        System.out.println("[5/6] Parsing risposta LLM e matching CREA...");
        ObjectMapper mapper = new ObjectMapper();
        JsonNode llmJson = parseLlmJson(llmResponseText, mapper);

        String candidateId = "crc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        RecipeCandidate candidate = new RecipeCandidate();
        candidate.candidateId      = candidateId;
        candidate.targetId         = targetId;
        candidate.dishName         = llmJson.path("dish").asText(dishName);
        candidate.declaredServings = llmJson.path("servings").asInt(servings);
        candidate.llmModel         = llmModel;
        candidate.llmPromptVersion = AnthropicClient.PROMPT_VERSION;
        candidate.reviewStatus     = "draft";

        // 5a. Processo ingredienti
        IngredientMatcher matcher = new IngredientMatcher(creaFoods);
        JsonNode ingredientsNode = llmJson.path("ingredients");
        int sortOrder = 0;

        double totalWeightG       = 0.0;
        double totalKcal          = 0.0;
        double totalProtein       = 0.0;
        double totalCarbs         = 0.0;
        double totalFat           = 0.0;
        double totalFiber         = 0.0;
        int    matchedCount       = 0;
        int    totalCount         = 0;
        List<String> unmatchedNames = new ArrayList<>();

        for (JsonNode ingNode : ingredientsNode) {
            String ingName = ingNode.path("name").asText("");
            double gramsRaw = ingNode.path("grams").asDouble(0.0);
            String role     = ingNode.path("role").asText("main");
            totalCount++;

            RecipeIngredientCandidate ing = new RecipeIngredientCandidate();
            ing.candidateId       = candidateId;
            ing.ingredientNameRaw = ingName;
            ing.gramsRaw          = gramsRaw;
            ing.gramsNormalized   = gramsRaw;
            ing.role              = role;
            ing.sortOrder         = sortOrder++;

            IngredientMatcher.MatchResult match = matcher.match(ingName, hintRefs);
            if (match != null) {
                CreaFood food = match.food;
                ing.creaCode       = food.code;
                ing.creaNameIt     = food.nameIt;
                ing.matchMethod    = match.method;
                ing.matchConfidence = match.confidence;

                double yf = yieldTable.getYieldFactor(food.code);
                ing.yieldFactor           = yf;
                ing.weightContributionG   = gramsRaw * yf;

                // Kcal contribution (basata sui grammi crudi, per 100g)
                double kcalPer100g = food.kcal != null ? food.kcal : 0.0;
                ing.kcalContribution = (kcalPer100g / 100.0) * gramsRaw;

                // Accumula totali (su gramsRaw per nutrizione; su weightContribution per peso)
                totalWeightG += ing.weightContributionG;
                totalKcal    += ing.kcalContribution;
                if (food.proteinG != null) totalProtein += (food.proteinG / 100.0) * gramsRaw;
                if (food.carbsG   != null) totalCarbs   += (food.carbsG   / 100.0) * gramsRaw;
                if (food.fatG     != null) totalFat     += (food.fatG     / 100.0) * gramsRaw;
                if (food.fiberG   != null) totalFiber   += (food.fiberG   / 100.0) * gramsRaw;
                matchedCount++;
            } else {
                unmatchedNames.add(ingName);
                // peso approssimato senza CREA
                totalWeightG += gramsRaw;
            }

            candidate.ingredients.add(ing);
        }

        // 5b. Calcola valori per 100g
        if (totalWeightG > 0) {
            candidate.computedWeightG = round2(totalWeightG);
            candidate.kcalPer100g     = round2(totalKcal    / totalWeightG * 100.0);
            candidate.proteinPer100g  = round2(totalProtein / totalWeightG * 100.0);
            candidate.carbsPer100g    = round2(totalCarbs   / totalWeightG * 100.0);
            candidate.fatPer100g      = round2(totalFat     / totalWeightG * 100.0);
            candidate.fiberPer100g    = round2(totalFiber   / totalWeightG * 100.0);
            candidate.defaultPortionG = round2(totalWeightG / candidate.declaredServings);
        }

        // 5c. Coverage e confidence
        double coveragePct = totalCount > 0 ? (double) matchedCount / totalCount * 100.0 : 0.0;
        candidate.creaCoveragePct = round2(coveragePct);
        candidate.confidenceLevel = coveragePct >= 75.0 ? "high" : (coveragePct >= 50.0 ? "medium" : "low");

        if (!unmatchedNames.isEmpty()) {
            candidate.qualityFlags = "unmatched:" + String.join(";", unmatchedNames);
        }

        // 6. Salva nel DB e su file
        System.out.println("[6/6] Salvataggio nel DB e su file...");
        db.upsertRecipeCandidate(candidate);
        db.replaceRecipeIngredients(candidateId, candidate.ingredients);

        // Esporta JSON review
        File artifactsDir = new File(ARTIFACTS_DIR);
        if (!artifactsDir.exists()) artifactsDir.mkdirs();
        String slug = slugify(candidate.dishName) + "_" + candidateId.substring(4, 12);
        File reviewFile = new File(artifactsDir, slug + ".review.json");
        ObjectMapper prettyMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        prettyMapper.writeValue(reviewFile, candidate);

        // Stampa summary
        System.out.println();
        System.out.println("=== SUMMARY ===");
        System.out.printf("  candidate_id   : %s%n", candidateId);
        System.out.printf("  dish_name      : %s%n", candidate.dishName);
        System.out.printf("  servings       : %d%n", candidate.declaredServings);
        System.out.printf("  ingredienti    : %d (matched %d / %d)%n", totalCount, matchedCount, totalCount);
        System.out.printf("  peso totale    : %.1f g%n", totalWeightG);
        System.out.printf("  kcal/100g      : %.1f%n", candidate.kcalPer100g != null ? candidate.kcalPer100g : 0.0);
        System.out.printf("  crea_coverage  : %.1f%%%n", coveragePct);
        System.out.printf("  confidence     : %s%n", candidate.confidenceLevel);
        if (!unmatchedNames.isEmpty()) {
            System.out.println("  unmatched      : " + unmatchedNames);
        }
        System.out.println("  review_file    : " + reviewFile.getAbsolutePath());
        System.out.println("  review_status  : draft");
        System.out.println("===============");
    }

    // -------------------------------------------------------------------------
    // Prompt builder
    // -------------------------------------------------------------------------

    private String buildPrompt(String dishName, int servings, List<CuratedRecipeTargetCreaRef> hintRefs) {
        StringBuilder hintSection = new StringBuilder();
        if (hintRefs != null && !hintRefs.isEmpty()) {
            hintSection.append("INGREDIENTI GIA' IDENTIFICATI NEL DATABASE NUTRIZIONALE CREA.\n");
            hintSection.append("VINCOLO: usa questi nomi ESATTI (o sinonimi stretti) per gli ingredienti corrispondenti.\n");
            hintSection.append("Non sostituire con varianti diverse (es. non 'fresco' se il riferimento e' 'sott'olio').\n");
            for (CuratedRecipeTargetCreaRef ref : hintRefs) {
                // Mostra label umano + nome CREA ufficiale per massima chiarezza all'LLM
                String label    = ref.label     != null ? ref.label     : ref.creaCode;
                String creaName = ref.creaNameIt != null ? " [CREA: " + ref.creaNameIt + "]" : "";
                hintSection.append("- ").append(label).append(creaName)
                           .append(" (codice: ").append(ref.creaCode).append(")\n");
            }
        }

        String hintStr = hintSection.length() > 0 ? hintSection.toString() : "";

        return "Sei un esperto di cucina italiana tradizionale.\n" +
               "Fornisci la lista ingredienti tipici per \"" + dishName + "\" calcolata per " + servings + " porzione/i.\n" +
               "\n" +
               "VINCOLI OBBLIGATORI (non derogabili):\n" +
               "1. Non riprodurre ricette da fonti specifiche (siti web, libri, chef, brand).\n" +
               "   Sintetizza dalla conoscenza distribuita della cucina italiana tradizionale.\n" +
               "2. Usa nomi generici degli ingredienti (es. \"pasta di semola secca\" non \"spaghetti marca X\").\n" +
               "3. Fornisci SOLO ingredienti e quantita' in grammi. Nessuna procedura, nessuna narrativa.\n" +
               "4. Se un ingrediente aromatico e' usato in quantita' trascurabile: includilo con i grams reali.\n" +
               (hintStr.isEmpty() ? "" : hintStr + "\n") +
               "Rispondi SOLO con questo JSON, senza testo prima o dopo:\n" +
               "{\n" +
               "  \"dish\": \"<nome piatto normalizzato>\",\n" +
               "  \"servings\": <numero intero>,\n" +
               "  \"ingredients\": [\n" +
               "    { \"name\": \"<nome ingrediente generico>\", \"grams\": <numero>, \"role\": \"main|sauce|seasoning|aroma\" }\n" +
               "  ]\n" +
               "}\n" +
               "\n" +
               "PIATTO: " + dishName + "\n" +
               "PORZIONI: " + servings + "\n";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Parsifica la risposta LLM estraendo il JSON.
     * Gestisce il caso in cui il modello aggiunga markdown o testo prima/dopo.
     */
    private JsonNode parseLlmJson(String text, ObjectMapper mapper) throws IOException {
        String trimmed = text.trim();
        // Rimuovi markdown code block se presente
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastBacktick = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastBacktick > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastBacktick).trim();
            }
        }
        // Trova il primo { e l'ultimo }
        int start = trimmed.indexOf('{');
        int end   = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }
        try {
            return mapper.readTree(trimmed);
        } catch (IOException e) {
            throw new IOException("Impossibile parsificare la risposta LLM come JSON. Testo ricevuto:\n" + text, e);
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String slugify(String name) {
        if (name == null) return "unknown";
        return name.toLowerCase(java.util.Locale.ITALIAN)
                   .replaceAll("[^a-z0-9]+", "_")
                   .replaceAll("^_|_$", "")
                   .substring(0, Math.min(40, name.length()));
    }
}
