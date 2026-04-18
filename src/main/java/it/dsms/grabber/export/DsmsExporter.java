package it.dsms.grabber.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import it.dsms.grabber.crea.CreaFood;
import it.dsms.grabber.db.PostgresConnector;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * Legge tutti i record da crea_foods e li serializza in un file .dsms.
 *
 * Uso da Main:
 *   DsmsExporter exporter = new DsmsExporter(connector);
 *   exporter.export(new File("crea_export.dsms"));
 */
public class DsmsExporter {

    private final PostgresConnector db;
    private final ObjectMapper mapper;

    public DsmsExporter(PostgresConnector db) {
        this.db = db;
        this.mapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public void export(File outputFile) throws SQLException, IOException {
        List<CreaFood> foods = db.findAllCrea();

        List<DsmsRecord> records = foods.stream()
                .map(DsmsRecord::from)
                .toList();

        DsmsExportManifest manifest = new DsmsExportManifest();
        manifest.count = records.size();
        manifest.items = records;

        mapper.writeValue(outputFile, manifest);

        System.out.printf("Esportati %d alimenti → %s%n", manifest.count, outputFile.getAbsolutePath());
        System.out.printf("seed_version: %s%n", manifest.seedVersion);
    }
}
