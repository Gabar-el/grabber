package it.dsms.grabber.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.dsms.grabber.crea.CreaFood;

/**
 * Rappresentazione di un singolo alimento nel crea_seed.json.
 *
 * - id       : "crea_" + codice CREA (es. "crea_005000") — chiave stabile in Isar.
 * - name_it  : denominazione ufficiale italiana CREA.
 * - name_en  : nome inglese — usato come alias EN in Flutter.
 * - name_sci : nome scientifico — metadato opzionale, non usato nel dominio DSMS.
 * - category : categoria CREA (es. "verdura e ortaggi").
 * - kcal … fiber_g : valori per 100 g.
 * - portion_g: porzione standard CREA — diventa FoodPortion di default in Flutter.
 *
 * water_g non viene esportata: non usata nel dominio DSMS.
 * I campi null vengono omessi dal JSON (@JsonInclude NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DsmsRecord {

    @JsonProperty("id")        public String id;
    @JsonProperty("name_it")   public String nameIt;
    @JsonProperty("name_en")   public String nameEn;
    @JsonProperty("name_sci")  public String nameSci;
    @JsonProperty("category")  public String category;
    @JsonProperty("kcal")      public Double kcal;
    @JsonProperty("protein_g") public Double proteinG;
    @JsonProperty("carbs_g")   public Double carbsG;
    @JsonProperty("fat_g")     public Double fatG;
    @JsonProperty("fiber_g")   public Double fiberG;
    @JsonProperty("portion_g") public Double portionG;

    public static DsmsRecord from(CreaFood f) {
        DsmsRecord r  = new DsmsRecord();
        // Prefisso "crea_" garantisce unicita' rispetto agli ID USDA ("usda_<fdcId>")
        r.id        = "crea_" + f.code.strip();
        r.nameIt    = f.nameIt;
        r.nameEn    = f.nameEn;
        r.nameSci   = f.nameSci;
        r.category  = f.category;
        r.kcal      = f.kcal;
        r.proteinG  = f.proteinG;
        r.carbsG    = f.carbsG;
        r.fatG      = f.fatG;
        r.fiberG    = f.fiberG;
        // water_g esclusa intenzionalmente: non usata nel dominio Flutter DSMS
        r.portionG  = f.portionG;
        return r;
    }
}
