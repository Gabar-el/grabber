package it.dsms.grabber.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Alimento del database nutrizionale CREA (INRAN).
 * Tabella: crea_foods (~900 righe, popolata via comando grab).
 */
@Entity
@Table(name = "crea_foods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(of = {"code", "nameIt"})
@EqualsAndHashCode(of = "code")
public class CreaFoodEntity {

    @Id
    @Column(name = "code", length = 6, nullable = false)
    private String code;

    @Column(name = "name_it", nullable = false)
    private String nameIt;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_sci")
    private String nameSci;

    @Column(name = "category")
    private String category;

    @Column(name = "kcal", precision = 7, scale = 2)
    private BigDecimal kcal;

    @Column(name = "protein_g", precision = 7, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "carbs_g", precision = 7, scale = 2)
    private BigDecimal carbsG;

    @Column(name = "fat_g", precision = 7, scale = 2)
    private BigDecimal fatG;

    @Column(name = "fiber_g", precision = 7, scale = 2)
    private BigDecimal fiberG;

    @Column(name = "water_g", precision = 7, scale = 2)
    private BigDecimal waterG;

    @Column(name = "portion_g", precision = 7, scale = 2)
    private BigDecimal portionG;

    @Column(name = "scraped_at", insertable = false, updatable = false)
    private OffsetDateTime scrapedAt;
}
