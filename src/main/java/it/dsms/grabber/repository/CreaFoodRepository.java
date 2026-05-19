package it.dsms.grabber.repository;

import it.dsms.grabber.entity.CreaFoodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreaFoodRepository extends JpaRepository<CreaFoodEntity, String> {

    /** Ricerca per nome (usata dalla pagina /crea/foods e dall'IngredientMatcher). */
    @Query(value = "SELECT * FROM crea_foods WHERE name_it ILIKE '%' || :q || '%'",
           nativeQuery = true)
    List<CreaFoodEntity> searchByName(@Param("q") String q);

    /** Tutti gli alimenti di una categoria. */
    @Query(value = "SELECT * FROM crea_foods WHERE category = :category",
           nativeQuery = true)
    List<CreaFoodEntity> findByCategory(@Param("category") String category);
}
