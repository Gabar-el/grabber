package it.dsms.grabber.crea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Recupera la lista di tutti i codici alimento dalla BDA CREA tramite
 * l'endpoint AJAX Joomla del plugin Alicat.
 *
 * Endpoint verificato:
 *   POST https://www.alimentinutrizione.it/index.php
 *        ?option=com_ajax&plugin=Alicat&method=Alicat&format=json
 *   Content-Type: application/x-www-form-urlencoded
 *   Body: cat=<codice_categoria>
 *
 * Risposta Joomla:
 *   { "success": true, "data": [[{"ALI_ID":"001010","ALI_DESC":"Crackers..."},...]] }
 *   Nota: data è un array doppio — data[0] è la lista effettiva.
 */
public class CreaIdFetcher {

    private static final String AJAX_URL =
            "https://www.alimentinutrizione.it/index.php" +
            "?option=com_ajax&plugin=Alicat&method=Alicat&format=json";

    private static final MediaType FORM = MediaType.get("application/x-www-form-urlencoded");

    /**
     * Categorie BDA CREA con codice numerico ufficiale.
     * Fonte: select#catesearch nella pagina di ricerca per categoria.
     */
    private static final Map<String, String> CATEGORIES = Map.ofEntries(
            Map.entry("01", "Cereali e derivati"),
            Map.entry("02", "Legumi"),
            Map.entry("03", "Verdure e ortaggi"),
            Map.entry("04", "Frutta"),
            Map.entry("05", "Frutta secca e semi oleaginosi"),
            Map.entry("06", "Carni fresche"),
            Map.entry("07", "Carni trasformate e conservate"),
            Map.entry("08", "Fast-food a base di carne"),
            Map.entry("09", "Frattaglie"),
            Map.entry("10", "Prodotti della pesca"),
            Map.entry("11", "Latte e yogurt"),
            Map.entry("12", "Formaggi e latticini"),
            Map.entry("13", "Uova"),
            Map.entry("14", "Oli e grassi"),
            Map.entry("15", "Dolci"),
            Map.entry("16", "Prodotti vari"),
            Map.entry("17", "Bevande alcoliche"),
            Map.entry("18", "Alimenti etnici"),
            Map.entry("19", "Ricette italiane"),
            Map.entry("20", "Alimenti tradizionali")
    );

    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public CreaIdFetcher() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Itera su tutte le categorie e restituisce la lista deduplicata di codici ALI_ID.
     */
    public List<String> fetchAll() throws IOException, InterruptedException {
        Set<String> seen = new LinkedHashSet<>();

        // Ordine stabile per codice numerico
        List<String> sortedKeys = new ArrayList<>(CATEGORIES.keySet());
        sortedKeys.sort(String::compareTo);

        for (String code : sortedKeys) {
            try {
                List<String> ids = fetchCategory(code);
                System.out.printf("  [%s] %-35s → %3d alimenti%n",
                        code, CATEGORIES.get(code), ids.size());
                seen.addAll(ids);
            } catch (IOException e) {
                System.err.printf("  WARN [%s] %-35s ignorata: %s%n",
                        code, CATEGORIES.get(code), e.getMessage());
            }
            Thread.sleep(500);
        }

        return new ArrayList<>(seen);
    }

    // -------------------------------------------------------------------------

    private List<String> fetchCategory(String categoryCode) throws IOException {
        RequestBody body = RequestBody.create("cat=" + categoryCode, FORM);
        Request request = new Request.Builder()
                .url(AJAX_URL)
                .post(body)
                .header("User-Agent", "Mozilla/5.0 (compatible; DSMSGrabber/1.0)")
                .header("Referer", "https://www.alimentinutrizione.it/tabelle-nutrizionali/ricerca-per-categoria")
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return parseCodes(response.body().string());
        }
    }

    /**
     * Estrae la lista di ALI_ID dalla risposta Joomla.
     * La struttura è: { "success": true, "data": [[ {ALI_ID, ALI_DESC}, ... ]] }
     * data è un array doppio: il primo elemento contiene la lista effettiva.
     */
    private List<String> parseCodes(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        List<String> codes = new ArrayList<>();

        JsonNode dataOuter = root.path("data");
        if (!dataOuter.isArray() || dataOuter.isEmpty()) return codes;

        // data[0] è la lista degli alimenti
        JsonNode items = dataOuter.get(0);
        if (!items.isArray()) return codes;

        for (JsonNode item : items) {
            JsonNode aliId = item.path("ALI_ID");
            if (!aliId.isMissingNode() && aliId.isTextual()) {
                String id = aliId.asText().trim();
                if (!id.isEmpty()) codes.add(id);
            }
        }
        return codes;
    }
}
