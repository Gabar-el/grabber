package it.dsms.grabber.service;

import it.dsms.grabber.entity.CuratedRecipeTargetCreaRefEntity;
import it.dsms.grabber.entity.CuratedRecipeTargetEntity;
import it.dsms.grabber.repository.CuratedRecipeTargetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Servizio per la gestione dei target di curation.
 *
 * <p>Espone:
 * <ul>
 *   <li>{@link #getTargets} — lista paginata con filtri opzionali
 *   <li>{@link #getTargetById} — dettaglio con refs CREA e candidati
 *   <li>{@link #updateRefs} — aggiorna i refs CREA di un target
 *   <li>Contatori aggregati per dashboard
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TargetService {

    private final CuratedRecipeTargetRepository targetRepository;

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Lista paginata di target con filtri opzionali.
     *
     * @param mealArea filtro meal_area (null = tutti)
     * @param status   filtro review_status (null = tutti)
     * @param q        ricerca testuale su dish_query (null = tutti)
     * @param pageable paginazione e sorting
     */
    public Page<CuratedRecipeTargetEntity> getTargets(String mealArea, String status,
                                                      String q, Pageable pageable) {
        return targetRepository.findByFilters(mealArea, status, q, pageable);
    }

    /**
     * Dettaglio target con refs CREA e candidati.
     *
     * @throws IllegalArgumentException se il target non esiste
     */
    public CuratedRecipeTargetEntity getTargetById(String targetId) {
        return targetRepository.findById(targetId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Target non trovato: " + targetId));
    }

    // -------------------------------------------------------------------------
    // Aggiornamento refs CREA
    // -------------------------------------------------------------------------

    /**
     * Sostituisce completamente i refs CREA di un target.
     *
     * <p>La lista esistente viene rimossa (orphanRemoval=true) e sostituita
     * con quella nuova. Il target viene salvato in modo transazionale.
     *
     * @param targetId id del target
     * @param newRefs  lista completa dei nuovi refs
     * @return target aggiornato
     * @throws IllegalArgumentException se il target non esiste
     */
    @Transactional
    public CuratedRecipeTargetEntity updateRefs(String targetId,
                                                List<CuratedRecipeTargetCreaRefEntity> newRefs) {
        CuratedRecipeTargetEntity target = getTargetById(targetId);

        // Rimuovi refs esistenti e aggiungi i nuovi
        target.getCreaRefs().clear();
        newRefs.forEach(ref -> ref.setTarget(target));
        target.getCreaRefs().addAll(newRefs);

        CuratedRecipeTargetEntity saved = targetRepository.save(target);
        log.info("updateRefs: target {} → {} refs CREA aggiornati", targetId, newRefs.size());
        return saved;
    }

    // -------------------------------------------------------------------------
    // Contatori (dashboard)
    // -------------------------------------------------------------------------

    public long countByStatus(String status) {
        return targetRepository.countByReviewStatus(status);
    }
}
