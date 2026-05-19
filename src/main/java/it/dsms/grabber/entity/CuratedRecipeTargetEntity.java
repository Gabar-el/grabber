package it.dsms.grabber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Target di curation: piatto da curare con LLM.
 * Tabella: curated_recipe_targets (1051 righe nel backlog iniziale).
 *
 * <p>review_status: pending | approved | rejected | needs_review
 * <p>crea_feasibility: high | medium | low
 * <p>meal_area: antipasto | primo | secondo | contorno | spuntino
 */
@Entity
@Table(name = "curated_recipe_targets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"targetId", "dishQuery", "mealArea", "reviewStatus"})
@EqualsAndHashCode(of = "targetId")
public class CuratedRecipeTargetEntity {

    @Id
    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "dish_query", nullable = false)
    private String dishQuery;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "meal_area", nullable = false)
    private String mealArea;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "crea_feasibility", nullable = false)
    private String creaFeasibility;

    @Column(name = "notes")
    private String notes;

    @Column(name = "source_file", nullable = false)
    private String sourceFile;

    @Column(name = "source_line")
    private Integer sourceLine;

    @Column(name = "review_status", nullable = false)
    @Builder.Default
    private String reviewStatus = "pending";

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Refs CREA associati a questo target. */
    @OneToMany(mappedBy = "target", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<CuratedRecipeTargetCreaRefEntity> creaRefs = new ArrayList<>();

    /** Candidati ricetta generati per questo target. */
    @OneToMany(mappedBy = "target", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<RecipeCandidateEntity> candidates = new ArrayList<>();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
