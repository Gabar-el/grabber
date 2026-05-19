package it.dsms.grabber.repository;

import it.dsms.grabber.entity.RecipeCandidateEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeCandidateRepository extends JpaRepository<RecipeCandidateEntity, String> {

    /**
     * Lista candidati filtrata per review_status e meal_area del target associato.
     * Tutti i parametri sono opzionali (null = ignora filtro).
     */
    @Query(value = """
            SELECT c.* FROM recipe_candidates c
            LEFT JOIN curated_recipe_targets t ON t.target_id = c.target_id
            WHERE (:status   IS NULL OR c.review_status = :status)
              AND (:mealArea IS NULL OR t.meal_area      = :mealArea)
            ORDER BY c.created_at DESC
            """,
           countQuery = """
            SELECT COUNT(*) FROM recipe_candidates c
            LEFT JOIN curated_recipe_targets t ON t.target_id = c.target_id
            WHERE (:status   IS NULL OR c.review_status = :status)
              AND (:mealArea IS NULL OR t.meal_area      = :mealArea)
            """,
           nativeQuery = true)
    Page<RecipeCandidateEntity> findByFilters(@Param("status")   String status,
                                              @Param("mealArea") String mealArea,
                                              Pageable pageable);

    /** Tutti i candidati approvati (usati per export-seed). */
    @Query(value = "SELECT * FROM recipe_candidates WHERE review_status = 'approved' ORDER BY created_at ASC",
           nativeQuery = true)
    List<RecipeCandidateEntity> findAllApproved();

    /** Candidati di un target specifico. */
    @Query(value = "SELECT * FROM recipe_candidates WHERE target_id = :targetId ORDER BY created_at DESC",
           nativeQuery = true)
    List<RecipeCandidateEntity> findByTargetId(@Param("targetId") String targetId);

    /** Aggiorna review_status e updated_at atomicamente. */
    @Modifying
    @Query(value = "UPDATE recipe_candidates SET review_status = :status, updated_at = now() WHERE candidate_id = :candidateId",
           nativeQuery = true)
    int updateReviewStatus(@Param("candidateId") String candidateId,
                           @Param("status")      String status);

    /** Conteggio candidati approvati (usato da SeedStatus). */
    @Query(value = "SELECT COUNT(*) FROM recipe_candidates WHERE review_status = 'approved'",
           nativeQuery = true)
    long countApproved();
}
