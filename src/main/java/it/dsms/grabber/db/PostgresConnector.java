package it.dsms.grabber.db;

import it.dsms.grabber.crea.CreaFood;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PostgresConnector {

    private static final String URL  = "jdbc:postgresql://localhost:5432/dsms_grabber";
    private static final String USER = "grabber";
    private static final String PASS = "grabber_pw";

    private final Connection connection;

    public PostgresConnector() throws SQLException {
        this.connection = DriverManager.getConnection(URL, USER, PASS);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    public void upsert(CreaFood food) throws SQLException {
        String sql = """
                INSERT INTO crea_foods
                    (code, name_it, name_en, name_sci, category,
                     kcal, protein_g, carbs_g, fat_g, fiber_g, water_g, portion_g)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT (code) DO UPDATE SET
                    name_it   = EXCLUDED.name_it,
                    name_en   = EXCLUDED.name_en,
                    name_sci  = EXCLUDED.name_sci,
                    category  = EXCLUDED.category,
                    kcal      = EXCLUDED.kcal,
                    protein_g = EXCLUDED.protein_g,
                    carbs_g   = EXCLUDED.carbs_g,
                    fat_g     = EXCLUDED.fat_g,
                    fiber_g   = EXCLUDED.fiber_g,
                    water_g   = EXCLUDED.water_g,
                    portion_g = EXCLUDED.portion_g,
                    scraped_at = now()
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, food.code);
            ps.setString(2, food.nameIt);
            ps.setString(3, food.nameEn);
            ps.setString(4, food.nameSci);
            ps.setString(5, food.category);
            setNullableDouble(ps, 6,  food.kcal);
            setNullableDouble(ps, 7,  food.proteinG);
            setNullableDouble(ps, 8,  food.carbsG);
            setNullableDouble(ps, 9,  food.fatG);
            setNullableDouble(ps, 10, food.fiberG);
            setNullableDouble(ps, 11, food.waterG);
            setNullableDouble(ps, 12, food.portionG);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<CreaFood> findAllCrea() throws SQLException {
        String sql = """
                SELECT code, name_it, name_en, name_sci, category,
                       kcal, protein_g, carbs_g, fat_g, fiber_g, water_g, portion_g
                FROM crea_foods
                ORDER BY code
                """;
        List<CreaFood> result = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs  = st.executeQuery(sql)) {
            while (rs.next()) {
                CreaFood f = new CreaFood();
                f.code      = rs.getString("code");
                f.nameIt    = rs.getString("name_it");
                f.nameEn    = rs.getString("name_en");
                f.nameSci   = rs.getString("name_sci");
                f.category  = rs.getString("category");
                f.kcal      = nullableDouble(rs, "kcal");
                f.proteinG  = nullableDouble(rs, "protein_g");
                f.carbsG    = nullableDouble(rs, "carbs_g");
                f.fatG      = nullableDouble(rs, "fat_g");
                f.fiberG    = nullableDouble(rs, "fiber_g");
                f.waterG    = nullableDouble(rs, "water_g");
                f.portionG  = nullableDouble(rs, "portion_g");
                result.add(f);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.NUMERIC);
        else               ps.setDouble(idx, value);
    }

    private Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
