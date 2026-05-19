package it.dsms.grabber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Candidato ricetta curata con LLM.
 * Tabella: recipe_candidates.
 *
 * <p>review_status: draft | reviewable | approved | rejected
 * <p>confidence_level: high | medium | low
 * <p>candidate_id formato: "crc_" + hex hash
 */
@Entity
@Table(name = "recipe_candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"candidateId", "dishName", "reviewStatus", "kcalPer100g"})
@EqualsAndHashCode(of = "candidateId")
public class RecipeCandidateEntity {

    @Id
    @Column(name = "candidate_id", nullable = false)
    private String candidateId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id")
    private CuratedRecipeTargetEntity target;

    @Column(name = "dish_name", nullable = false)
    private String dishName;

    @Column(name = "declared_servings", nullable = false)
    @Builder.Default
    private int declaredServings = 1;

    @Column(name = "computed_weight_g", precision = 8, scale = 2)
    private BigDecimal computedWeightG;

    @Column(name = "kcal_per_100g", precision = 7, scale = 2)
    private BigDecimal kcalPer100g;

    @Column(name = "protein_per_100g", precision = 7, scale = 2)
    private BigDecimal proteinPer100g;

    @Column(name = "carbs_per_100g", precision = 7, scale = 2)
    private BigDecimal carbsPer100g;

    @Column(name = "fat_per_100g", precision = 7, scale = 2)
    private BigDecimal fatPer100g;

    @Column(name = "fiber_per_100g", precision = 7, scale = 2)
    private BigDecimal fiberPer100g;

    @Column(name = "default_portion_g", precision = 7, scale = 2)
    private BigDecimal defaultPortionG;

    @Column(name = "crea_coverage_pct", precision = 5, scale = 2)
    private BigDecimal creaCoveragePct;

    @Column(name = "confidence_level")
    private String confidenceLevel;

    @Column(name = "source_ref", nullable = false)
    @Builder.Default
    private String sourceRef = "internal_curation_llm_assisted";

    @Column(name = "extraction_method", nullable = false)
    @Builder.Default
    private String extractionMethod = "llm_assisted";

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "llm_prompt_version")
    private String llmPromptVersion;

    @Column(name = "review_status", nullable = false)
    @Builder.Default
    private String reviewStatus = "draft";

    @Column(name = "quality_flags")
    private String qualityFlags;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Ingredienti di questo candidato. */
    @OneToMany(mappedBy = "candidate", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    @Builder.Default
    private List<RecipeIngredientCandidateEntity> ingredients = new ArrayList<>();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
