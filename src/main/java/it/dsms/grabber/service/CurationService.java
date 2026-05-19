package it.dsms.grabber.service;

import it.dsms.grabber.entity.RecipeCandidateEntity;
import it.dsms.grabber.repository.RecipeCandidateRepository;
import it.dsms.grabber.service.ai.AiAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servizio che espone le operazioni di curation: generate, approve, reject.
 *
 * <p>Le operazioni di approvazione/rifiuto sono {@code @Transactional} per
 * garantire atomicità dell'aggiornamento dello status.
 *
 * <p>La generazione vera e propria (CurateRecipeCommand) rimane nel motore
 * engine/ e si appoggia ancora a PostgresConnector durante la transizione
 * (Phase 2–3). In Phase 3 il delegate REST chiamerà questo servizio.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurationService {

    private final RecipeCandidateRepository candidateRepository;
    private final AiAgentService            aiAgentService;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Approva un candidato: transizione draft | reviewable → approved.
     *
     * @param candidateId id del candidato (formato crc_...)
     * @return entity aggiornata
     * @throws IllegalArgumentException se il candidato non esiste
     * @throws IllegalStateException    se il candidato è già rejected
     */
    @Transactional
    public RecipeCandidateEntity approve(String candidateId) {
        RecipeCandidateEntity candidate = findOrThrow(candidateId);

        if ("approved".equals(candidate.getReviewStatus())) {
            log.warn("approve: candidato {} già in stato 'approved', nessuna modifica", candidateId);
            return candidate;
        }
        if ("rejected".equals(candidate.getReviewStatus())) {
            throw new IllegalStateException(
                "Il candidato " + candidateId + " è in stato 'rejected'. " +
                "Non può essere approvato direttamente: rigenera con curate-recipe.");
        }

        int updated = candidateRepository.updateReviewStatus(candidateId, "approved");
        if (updated == 0) {
            throw new IllegalStateException("Aggiornamento DB fallito per " + candidateId);
        }

        log.info("approve: candidato {} → approved", candidateId);
        candidate.setReviewStatus("approved");
        return candidate;
    }

    /**
     * Rigetta un candidato: qualsiasi stato → rejected.
     *
     * @param candidateId id del candidato
     * @param note        nota opzionale (loggata, non persiste in DB per ora)
     * @return entity aggiornata
     * @throws IllegalArgumentException se il candidato non esiste
     */
    @Transactional
    public RecipeCandidateEntity reject(String candidateId, String note) {
        RecipeCandidateEntity candidate = findOrThrow(candidateId);

        if ("rejected".equals(candidate.getReviewStatus())) {
            log.warn("reject: candidato {} già in stato 'rejected', nessuna modifica", candidateId);
            return candidate;
        }

        int updated = candidateRepository.updateReviewStatus(candidateId, "rejected");
        if (updated == 0) {
            throw new IllegalStateException("Aggiornamento DB fallito per " + candidateId);
        }

        if (note != null && !note.isBlank()) {
            log.info("reject: candidato {} → rejected (nota: {})", candidateId, note);
        } else {
            log.info("reject: candidato {} → rejected", candidateId);
        }

        candidate.setReviewStatus("rejected");
        return candidate;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Carica un candidato per id.
     *
     * @throws IllegalArgumentException se non esiste
     */
    public RecipeCandidateEntity findOrThrow(String candidateId) {
        return candidateRepository.findById(candidateId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Candidato non trovato: " + candidateId));
    }

    // -------------------------------------------------------------------------
    // Info
    // -------------------------------------------------------------------------

    /** Modello LLM attualmente configurato (usato dal delegate per la UI). */
    public String currentModel() {
        return aiAgentService.getModel();
    }
}
