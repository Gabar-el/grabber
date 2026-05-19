package it.dsms.grabber.repository;

import it.dsms.grabber.entity.CuratedRecipeTargetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CuratedRecipeTargetRepository extends JpaRepository<CuratedRecipeTargetEntity, String> {

    /**
     * Lista target filtrata per meal_area, review_status e testo libero su dish_query.
     * Tutti i parametri sono opzionali (null = ignora filtro).
     */
    @Query(value = """
            SELECT * FROM curated_recipe_targets
            WHERE (:mealArea    IS NULL OR meal_area     = :mealArea)
              AND (:status      IS NULL OR review_status = :status)
              AND (:q           IS NULL OR dish_query ILIKE '%' || :q || '%')
            ORDER BY priority ASC NULLS LAST, target_id ASC
            """,
           countQuery = """
            SELECT COUNT(*) FROM curated_recipe_targets
            WHERE (:mealArea    IS NULL OR meal_area     = :mealArea)
              AND (:status      IS NULL OR review_status = :status)
              AND (:q           IS NULL OR dish_query ILIKE '%' || :q || '%')
            """,
           nativeQuery = true)
    Page<CuratedRecipeTargetEntity> findByFilters(@Param("mealArea") String mealArea,
                                                  @Param("status")   String status,
                                                  @Param("q")        String q,
                                                  Pageable pageable);

    /** Conteggio per review_status (usato dalla dashboard). */
    @Query(value = "SELECT COUNT(*) FROM curated_recipe_targets WHERE review_status = :status",
           nativeQuery = true)
    long countByReviewStatus(@Param("status") String status);

    /** Target con almeno un candidato approved (usato per escludere dal backlog). */
    @Query(value = """
            SELECT t.* FROM curated_recipe_targets t
            WHERE EXISTS (
                SELECT 1 FROM recipe_candidates c
                WHERE c.target_id = t.target_id
                  AND c.review_status = 'approved'
            )
            """,
           nativeQuery = true)
    List<CuratedRecipeTargetEntity> findAllWithApprovedCandidate();
}
