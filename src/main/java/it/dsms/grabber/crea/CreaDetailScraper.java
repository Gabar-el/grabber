package it.dsms.grabber.crea;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estrae dati nutrizionali da una pagina di dettaglio CREA BDA.
 *
 * URL tipo: https://www.alimentinutrizione.it/tabelle-nutrizionali/001010
 *
 * Struttura HTML verificata:
 *
 *   <h1 class="article-title">Crackers, alla soia</h1>
 *
 *   <table class="toptable">        ← metadati alimento
 *     <tr><td>Categoria</td>   <td>Cereali e derivati</td></tr>
 *     <tr><td>English Name</td><td>Crackers with soybean</td></tr>
 *     <tr><td>Porzione</td>    <td>30 g</td></tr>
 *   </table>
 *
 *   <tr class="corponutriente">     ← una riga per nutriente
 *     <td>Acqua (g)</td>            col 1 = nome nutriente
 *     <td>g</td>                    col 2 = unità
 *     <td>2.9</td>                  col 3 = valore per 100 g  ← quello che ci serve
 *     <td></td>                     col 4 = min
 *     <td></td>                     col 5 = max
 *     <td>0.9</td>                  col 6 = valore per porzione
 *   </tr>
 */
public class CreaDetailScraper {

    private static final java.util.Set<String> NULL_VALUES =
            java.util.Set.of("-", "n.d.", "nd", "n.q.", "tr", "tracce", "");

    private static final Pattern PORTION_G_PATTERN =
            Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*g", Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------

    public CreaFood parse(String code, Document doc) {
        CreaFood food = new CreaFood();
        food.code = code;

        extractMeta(doc, food);
        extractNutrients(doc, food);

        return food;
    }

    // -------------------------------------------------------------------------
    // Metadati (nome, categoria, lingua, porzione)
    // -------------------------------------------------------------------------

    private void extractMeta(Document doc, CreaFood food) {
        // Nome italiano
        Element h1 = doc.selectFirst("h1.article-title");
        if (h1 != null) {
            food.nameIt = h1.text().trim();
        } else {
            // fallback: primo h1 qualsiasi, poi title della pagina
            Element anyH1 = doc.selectFirst("h1");
            if (anyH1 != null) {
                food.nameIt = anyH1.text().trim();
            } else {
                String title = doc.title();
                food.nameIt = title.contains("-") ? title.split("-")[0].trim() : title.trim();
            }
        }

        // Tabella metadati (table.toptable): prima colonna = etichetta, seconda = valore
        for (Element row : doc.select("table.toptable tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;

            String label = cells.get(0).text().toLowerCase().trim();
            String value = cells.get(1).text().trim();
            if (value.isEmpty()) continue;

            if (label.contains("categoria") || label.contains("category")) {
                food.category = value;
            } else if (label.contains("english name") || label.contains("nome inglese")) {
                food.nameEn = value;
            } else if (label.contains("nome scientifico") || label.contains("scientific")) {
                food.nameSci = value;
            } else if (label.contains("porzione") || label.contains("portion")) {
                food.portionG = parseGrams(value);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Nutrienti
    // -------------------------------------------------------------------------

    private void extractNutrients(Document doc, CreaFood food) {
        Map<String, Double> nm = new HashMap<>();

        // Le righe nutrienti hanno classe corponutriente (macro) o corpovitamine
        for (Element row : doc.select("tr.corponutriente, tr.corpovitamine")) {
            Elements cells = row.select("td");
            if (cells.size() < 3) continue;

            String label = cells.get(0).text().toLowerCase().trim();
            String raw   = cells.get(2).text().trim(); // col 3 = per 100 g

            if (label.isBlank() || isNull(raw)) continue;

            Double value = parseDouble(raw);
            if (value != null) nm.put(label, value);
        }

        food.kcal     = getFirst(nm, "energia (kcal)", "energia");
        food.proteinG = getFirst(nm, "proteine (g)", "proteine");
        food.fatG     = getFirst(nm, "lipidi (g)", "lipidi totali", "lipidi");
        food.carbsG   = getFirst(nm, "carboidrati disponibili (g)", "carboidrati disponibili", "carboidrati");
        food.fiberG   = getFirst(nm, "fibra totale (g)", "fibra alimentare (g)", "fibra totale", "fibra");
        food.waterG   = getFirst(nm, "acqua (g)", "acqua");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Parsifica "30 g" → 30.0 */
    private Double parseGrams(String s) {
        if (s == null) return null;
        Matcher m = PORTION_G_PATTERN.matcher(s);
        return m.find() ? parseDouble(m.group(1)) : null;
    }

    private boolean isNull(String s) {
        return NULL_VALUES.contains(s.toLowerCase().trim());
    }

    private Double parseDouble(String s) {
        if (s == null || isNull(s)) return null;
        try {
            return Double.parseDouble(s.replace(",", ".").replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double getFirst(Map<String, Double> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) return map.get(key);
            // matching per prefisso (gestisce eventuali variazioni di etichetta)
            for (Map.Entry<String, Double> entry : map.entrySet()) {
                if (entry.getKey().startsWith(key)) return entry.getValue();
            }
        }
        return null;
    }
}
