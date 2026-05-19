package it.dsms.grabber.entity;

import lombok.*;

import java.io.Serializable;

/**
 * Chiave composita per {@link CuratedRecipeTargetCreaRefEntity}.
 * Deve essere {@link Serializable} come richiesto dalla spec JPA.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CuratedRecipeTargetCreaRefId implements Serializable {

    /** target_id — corrisponde al campo {@code target} nell'entity (FK). */
    private String target;

    /** crea_code */
    private String creaCode;
}
