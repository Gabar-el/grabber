package it.dsms.grabber.curated;

import it.dsms.grabber.db.PostgresConnector;

/**
 * Comandi di lifecycle per i candidati ricetta:
 *
 *   approve-recipe <candidate_id>            — draft/reviewable → approved
 *   reject-recipe  <candidate_id> [note]     — qualsiasi stato  → rejected
 *
 * Transizioni consentite:
 *   approve:  draft | reviewable → approved   (warn se già approved, blocca se rejected)
 *   reject:   qualsiasi stato    → rejected   (warn se già rejected)
 */
public class ApproveRecipeCommand {

    private final PostgresConnector db;

    public ApproveRecipeCommand(PostgresConnector db) {
        this.db = db;
    }

    // -------------------------------------------------------------------------

    public void approve(String candidateId) throws Exception {
        RecipeCandidate c = loadOrFail(candidateId);

        if ("approved".equals(c.reviewStatus)) {
            System.out.println("WARN: candidato già in stato 'approved' — nessuna modifica.");
            printSummary(c);
            return;
        }
        if ("rejected".equals(c.reviewStatus)) {
            System.err.println("ERRORE: il candidato è in stato 'rejected'.");
            System.err.println("       Usa reject-recipe per gestire lo stato rejected, oppure ri-genera con curate-recipe.");
            System.exit(1);
        }

        boolean updated = db.setRecipeCandidateStatus(candidateId, "approved");
        if (!updated) {
            System.err.println("ERRORE: aggiornamento DB fallito per " + candidateId);
            System.exit(1);
        }

        c.reviewStatus = "approved";
        System.out.println("OK: candidato approvato.");
        printSummary(c);
    }

    public void reject(String candidateId, String note) throws Exception {
        RecipeCandidate c = loadOrFail(candidateId);

        if ("rejected".equals(c.reviewStatus)) {
            System.out.println("WARN: candidato già in stato 'rejected' — nessuna modifica.");
            printSummary(c);
            return;
        }

        boolean updated = db.setRecipeCandidateStatus(candidateId, "rejected");
        if (!updated) {
            System.err.println("ERRORE: aggiornamento DB fallito per " + candidateId);
            System.exit(1);
        }

        c.reviewStatus = "rejected";
        System.out.println("OK: candidato rigettato.");
        if (note != null && !note.isBlank()) {
            System.out.println("  nota: " + note);
        }
        printSummary(c);
    }

    // -------------------------------------------------------------------------

    private RecipeCandidate loadOrFail(String candidateId) throws Exception {
        RecipeCandidate c = db.findRecipeCandidateSummary(candidateId);
        if (c == null) {
            System.err.println("ERRORE: candidato '" + candidateId + "' non trovato nel DB.");
            System.exit(1);
        }
        return c;
    }

    private static void printSummary(RecipeCandidate c) {
        System.out.println();
        System.out.println("=== CANDIDATE SUMMARY ===");
        System.out.printf("  candidate_id   : %s%n", c.candidateId);
        System.out.printf("  target_id      : %s%n", c.targetId != null ? c.targetId : "(nessuno)");
        System.out.printf("  dish_name      : %s%n", c.dishName);
        System.out.printf("  servings       : %d%n", c.declaredServings);
        System.out.printf("  peso totale    : %.1f g%n", c.computedWeightG != null ? c.computedWeightG : 0.0);
        System.out.printf("  porzione       : %.1f g%n", c.defaultPortionG != null ? c.defaultPortionG : 0.0);
        System.out.printf("  kcal/100g      : %.1f%n",   c.kcalPer100g     != null ? c.kcalPer100g     : 0.0);
        System.out.printf("  proteine/100g  : %.1f g%n", c.proteinPer100g  != null ? c.proteinPer100g  : 0.0);
        System.out.printf("  carboidrati/100g: %.1f g%n",c.carbsPer100g    != null ? c.carbsPer100g    : 0.0);
        System.out.printf("  grassi/100g    : %.1f g%n", c.fatPer100g      != null ? c.fatPer100g      : 0.0);
        System.out.printf("  crea_coverage  : %.1f%%%n", c.creaCoveragePct != null ? c.creaCoveragePct : 0.0);
        System.out.printf("  confidence     : %s%n", c.confidenceLevel);
        System.out.printf("  llm_model      : %s%n", c.llmModel);
        if (c.qualityFlags != null) {
            System.out.printf("  quality_flags  : %s%n", c.qualityFlags);
        }
        System.out.printf("  review_status  : %s%n", c.reviewStatus);
        System.out.println("=========================");
    }
}
