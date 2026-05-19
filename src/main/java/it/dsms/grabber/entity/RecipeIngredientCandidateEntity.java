package it.dsms.grabber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Ingrediente di un candidato ricetta.
 * Tabella: recipe_ingredient_candidates (PK: id SERIAL).
 *
 * <p>{@code creaCode} è nullable: ingredienti non matchati al DB CREA
 * (IGNORABLE o custom CX) non hanno un codice CREA.
 * <p>{@code yieldFactor}: peso_finale_g = grams_raw * yield_factor (default 1.0).
 */
@Entity
@Table(name = "recipe_ingredient_candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"id", "ingredientNameRaw", "creaCode", "gramsRaw", "role"})
@EqualsAndHashCode(of = "id")
public class RecipeIngredientCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private RecipeCandidateEntity candidate;

    /** Nullable: ingredienti non matchati a CREA (IGNORABLE, CX custom). */
    @Column(name = "crea_code")
    private String creaCode;

    @Column(name = "ingredient_name_raw", nullable = false)
    private String ingredientNameRaw;

    @Column(name = "grams_raw", precision = 7, scale = 2)
    private BigDecimal gramsRaw;

    @Column(name = "grams_normalized", precision = 7, scale = 2)
    private BigDecimal gramsNormalized;

    /** peso_finale_g = grams_raw * yield_factor */
    @Column(name = "yield_factor", precision = 6, scale = 3, nullable = false)
    @Builder.Default
    private BigDecimal yieldFactor = BigDecimal.ONE;

    @Column(name = "weight_contribution_g", precision = 7, scale = 2)
    private BigDecimal weightContributionG;

    @Column(name = "kcal_contribution", precision = 7, scale = 2)
    private BigDecimal kcalContribution;

    @Column(name = "match_method")
    private String matchMethod;

    @Column(name = "match_confidence", precision = 5, scale = 3)
    private BigDecimal matchConfidence;

    /** main | condiment | garnish | liquid */
    @Column(name = "role")
    @Builder.Default
    private String role = "main";

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
