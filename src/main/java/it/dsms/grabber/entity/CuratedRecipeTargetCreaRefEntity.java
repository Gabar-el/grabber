package it.dsms.grabber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Ref CREA per un target di curation.
 * PK composita: (target_id, crea_code).
 * Tabella: curated_recipe_target_crea_refs (3693 righe).
 */
@Entity
@Table(name = "curated_recipe_target_crea_refs")
@IdClass(CuratedRecipeTargetCreaRefId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"creaCode", "label", "refOrder", "isPrimary"})
@EqualsAndHashCode(of = {"target", "creaCode"})
public class CuratedRecipeTargetCreaRefEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private CuratedRecipeTargetEntity target;

    @Id
    @Column(name = "crea_code", length = 6, nullable = false)
    private String creaCode;

    @Column(name = "ref_order", nullable = false)
    private int refOrder;

    @Column(name = "label")
    private String label;

    @Column(name = "role")
    private String role;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean isPrimary = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
