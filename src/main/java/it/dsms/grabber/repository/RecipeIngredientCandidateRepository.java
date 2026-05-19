package it.dsms.grabber.repository;

import it.dsms.grabber.entity.RecipeIngredientCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeIngredientCandidateRepository extends JpaRepository<RecipeIngredientCandidateEntity, Long> {

    /** Ingredienti di un candidato, ordinati per sort_order. */
    @Query(value = "SELECT * FROM recipe_ingredient_candidates WHERE candidate_id = :candidateId ORDER BY sort_order ASC",
           nativeQuery = true)
    List<RecipeIngredientCandidateEntity> findByCandidateId(@Param("candidateId") String candidateId);

    /** Ingredienti di tutti i candidati approved (usato da export-seed per evitare N+1). */
    @Query(value = """
            SELECT i.* FROM recipe_ingredient_candidates i
            JOIN recipe_candidates c ON c.candidate_id = i.candidate_id
            WHERE c.review_status = 'approved'
            ORDER BY i.candidate_id, i.sort_order ASC
            """,
           nativeQuery = true)
    List<RecipeIngredientCandidateEntity> findAllByApprovedCandidates();
}
