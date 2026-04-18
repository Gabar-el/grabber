# grabber_v001

## Scopo di questo documento

Fissare le decisioni architetturali e tecniche prese nella sessione del 14 aprile 2026
riguardo alla costruzione di un grabber Java autonomo per la creazione di un database
nutrizionale locale, senza dipendenza da API commerciali a pagamento.

---

## Contesto e motivazione

### Problema

Le API nutrizionali commerciali (Edaman, Nutritionix e simili) hanno costi incompatibili
con il budget zero di DSMS.

USDA FoodData Central è gratuita ma:
- database in inglese;
- i nomi italiani richiedono traduzione prima della ricerca;
- non copre alimenti tipicamente italiani con denominazioni locali.

Open Food Facts è gratuita ma al momento del test era down (503) e i dati branded
sono spesso incompleti o mal normalizzati.

### Soluzione adottata

Costruire un grabber Java che popola un database PostgreSQL locale con dati provenienti
da fonti autorevoli e accessibili liberamente, in particolare **CREA**.

---

## Fonte primaria: CREA

### Identità

**CREA** = Consiglio per la Ricerca in Agricoltura e l'Analisi dell'Economia Agraria.

Sito: `https://www.alimentinutrizione.it`

La Banca Dati di Composizione degli Alimenti (BDA) è il database nutrizionale ufficiale
del governo italiano. È l'equivalente italiano dell'USDA FoodData Central.

### Caratteristiche del dataset

- Circa **950 alimenti** italiani con denominazione italiana ufficiale
- Codice alimento a 6 cifre (es. `005000` = Aglio)
- Campi: Nome italiano, Nome scientifico, English name, Categoria
- Valori per 100 g e per porzione standard
- Nutrienti: Energia (kcal), Proteine, Lipidi, Carboidrati disponibili,
  Fibra, Acqua, e micronutrienti
- **Open data governativo**: nessuna restrizione legale allo scraping

### Pattern URL

```
# Pagina di dettaglio singolo alimento (SSR puro — Jsoup funziona direttamente)
https://www.alimentinutrizione.it/tabelle-nutrizionali/{CODICE_6CIFRE}

# Es.
https://www.alimentinutrizione.it/tabelle-nutrizionali/005000   # Aglio
https://www.alimentinutrizione.it/tabelle-nutrizionali/202020   # Caramelle mou
```

### Come ottenere la lista degli ID

La pagina di ricerca per categoria usa AJAX POST verso endpoint Joomla:

```
POST /index.php?option=com_ajax&plugin=Alicat&method=Alicat
Body: { "categoria": "verdura" }
Risposta: JSON con array di codici alimento
```

Richiede OkHttp (non Jsoup puro) per la chiamata POST.

### Struttura HTML pagina di dettaglio

La tabella nutrizionale è in HTML statico standard:

```html
<table>
  <tr>
    <th>Descrizione Nutriente</th>
    <th>Unità di misura</th>
    <th>Valore per 100 g</th>
    <th>Valore per porzione</th>
  </tr>
  <tr><td>Energia</td><td>kcal</td><td>41</td><td>...</td></tr>
  <tr><td>Proteine</td><td>g</td><td>1.86</td><td>...</td></tr>
  <tr><td>Lipidi totali</td><td>g</td><td>0.5</td><td>...</td></tr>
  <tr><td>Carboidrati disponibili</td><td>g</td><td>8.4</td><td>...</td></tr>
  ...
</table>
```

Parsing via Jsoup: cercare la riga con testo `"Energia"`, `"Proteine"`, `"Lipidi"`,
`"Carboidrati"` nel primo `<td>` e leggere il terzo `<td>` per il valore per 100 g.

### robots.txt

Nessuna restrizione su `/tabelle-nutrizionali/`. Blocca solo directory
di sistema Joomla (`/administrator/`, `/cache/`). Nessun `Crawl-delay` dichiarato.

---

## Fonte secondaria: Yazio

### Identità

`https://www.yazio.com/it/alimenti`

App commerciale di food tracking. Database crowdsourced + branded + porzioni pratiche.

### Decisione adottata

**Yazio da usare solo come riferimento secondario limitato** (non bulk scraping).

Motivazioni:
- Il robots.txt ha una blacklist esplicita di scraper noti, segnale di intenzione
  anti-scraping da parte del sito.
- I Termini di Servizio commerciali tipicamente vietano scraping sistematico.
- I dati CREA coprono già tutto il dominio DSMS per alimenti di uso comune.

### Struttura tecnica (per eventuali query puntuali)

Tecnologia: Nuxt.js con SSR ibrido. I dati nutrizionali NON sono in tag HTML semantici
ma sono presenti nell'HTML come JSON inline:

```html
<script>window.__NUXT__ = { ... nutrients: [...] ... }</script>
```

Il JSON contiene:
```json
{
  "nutrients": [
    {"amount": 149, "type": "energy.energy", "unit": "kcal"},
    {"amount": 33.06, "type": "nutrient.carb", "unit": "g"},
    {"amount": 0.5,   "type": "nutrient.fat",  "unit": "g"},
    {"amount": 6.36,  "type": "nutrient.protein", "unit": "g"}
  ],
  "slug": "aglio"
}
```

Pattern URL: `/it/alimenti/{slug}.html` — es. `/it/alimenti/aglio.html`

---

## Schema PostgreSQL

```sql
-- Tabella CREA: fonte autorevole italiana
CREATE TABLE crea_foods (
    code        CHAR(6)       PRIMARY KEY,      -- codice CREA (es. '005000')
    name_it     TEXT          NOT NULL,          -- 'Aglio'
    name_en     TEXT,                            -- 'Garlic'
    name_sci    TEXT,                            -- 'Allium sativum'
    category    TEXT,                            -- 'Verdura e ortaggi'
    kcal        NUMERIC(7,2),
    protein_g   NUMERIC(7,2),
    carbs_g     NUMERIC(7,2),
    fat_g       NUMERIC(7,2),
    fiber_g     NUMERIC(7,2),
    water_g     NUMERIC(7,2),
    portion_g   NUMERIC(7,2),                   -- porzione standard CREA
    scraped_at  TIMESTAMPTZ   DEFAULT now()
);

-- Tabella Yazio: porzioni pratiche e nomi colloquiali (uso limitato)
CREATE TABLE yazio_foods (
    slug        TEXT          PRIMARY KEY,       -- 'aglio'
    name_it     TEXT          NOT NULL,
    category    TEXT,
    kcal        NUMERIC(7,2),
    protein_g   NUMERIC(7,2),
    carbs_g     NUMERIC(7,2),
    fat_g       NUMERIC(7,2),
    scraped_at  TIMESTAMPTZ   DEFAULT now()
);
```

---

## Architettura del Grabber Java

### Stack tecnologico

| Libreria | Uso |
|---|---|
| **Jsoup** | Parsing HTML pagine di dettaglio CREA |
| **OkHttp** | HTTP client per POST AJAX (lista ID CREA) |
| **Jackson** | Parse JSON `window.__NUXT__` Yazio |
| **PostgreSQL JDBC** | Scrittura su DB locale |
| **Maven** | Build e dipendenze |

### Struttura moduli

```
grabber/
├── pom.xml
├── schema.sql
└── src/main/java/it/dsms/grabber/
    ├── Main.java                # orchestrazione, rate limiting
    ├── db/
    │   └── PostgresConnector.java
    ├── crea/
    │   ├── CreaIdFetcher.java   # POST AJAX → lista codici
    │   ├── CreaDetailScraper.java  # Jsoup → parsing tabella dettaglio
    │   └── CreaFood.java        # POJO
    └── yazio/
        ├── YazioScraper.java    # OkHttp + parse __NUXT__ JSON
        └── YazioFood.java       # POJO
```

### Pseudocodice CreaGrabber

```java
// Fase 1: recupera tutti i codici per categoria
List<String> codes = CreaIdFetcher.fetchAll(); // POST AJAX per ogni categoria

// Fase 2: per ogni codice, scrapa il dettaglio
for (String code : codes) {
    Document doc = Jsoup.connect(BASE_URL + code)
        .userAgent("Mozilla/5.0 (compatible)")
        .timeout(10_000)
        .get();

    CreaFood food = CreaDetailScraper.parse(code, doc);
    PostgresConnector.upsert(food);

    Thread.sleep(1_000); // crawl delay rispettoso: 1 req/sec
}
```

### Parsing tabella CREA

```java
Map<String, Double> nutrientMap = new HashMap<>();
for (Element row : doc.select("table tr")) {
    String label = row.select("td:first-child").text().toLowerCase();
    String val   = row.select("td:nth-child(3)").text().trim();
    if (!val.isEmpty() && !val.equals("-")) {
        nutrientMap.put(label, Double.parseDouble(val.replace(",", ".")));
    }
}
double kcal    = nutrientMap.getOrDefault("energia", 0.0);
double protein = nutrientMap.getOrDefault("proteine", 0.0);
double fat     = getFirstMatch(nutrientMap, "lipidi totali", "lipidi");
double carbs   = getFirstMatch(nutrientMap, "carboidrati disponibili", "carboidrati");
double fiber   = nutrientMap.getOrDefault("fibra totale", null);
```

---

## Flusso di integrazione con DSMS

```
[Grabber Java]
    │
    ▼
[PostgreSQL locale]
    crea_foods (~950 righe)
    yazio_foods (limitato)
    │
    ▼
[Script export]   ← genera JSON o Dart da crea_foods
    │
    ▼
[in_memory_food_catalog_local_data_source.dart]
    seed esteso (da 150 → 900+ alimenti)
    │
    ▼
[USDA FoodData Central]
    validazione incrociata tramite English Name da crea_foods
```

Il campo `name_en` di CREA permette di cercare lo stesso alimento su USDA per
validazione/arricchimento dei dati senza dipendere da un dizionario di traduzione
manuale.

---

## Rapporto con task IT→EN per USDA

Il dizionario IT→EN per USDA (task B, ancora da completare) è una soluzione
parziale e manuale al problema della lingua. Con il DB CREA diventa quasi superfluo
perché:

- CREA ha già `name_en` per ogni alimento
- Cross-match CREA ↔ USDA via English Name è più affidabile di una traduzione fuzzy

Il task B rimane comunque utile come fallback rapido per gli alimenti non ancora
nel DB CREA (principalmente branded e fast food).

---

## Stato al 14 aprile 2026

- [x] Analisi tecnica fonti completata
- [x] Schema PostgreSQL definito
- [x] Architettura Java definita
- [ ] Implementazione CreaIdFetcher
- [ ] Implementazione CreaDetailScraper
- [ ] Implementazione PostgresConnector
- [ ] Implementazione Main con rate limiting
- [ ] Test su subset di 10 alimenti
- [ ] Run completo (~950 alimenti)
- [ ] Script export → Dart seed

---

## Decisioni chiave da non ridiscutere

1. **CREA come fonte primaria**, non Yazio né Edaman/Nutritionix
2. **Yazio solo puntuale**, non bulk — rispetto del robots.txt e ToS
3. **USDA come validazione**, non come fonte primaria per italiano
4. **Rate limit 1 req/sec** su CREA — rispettoso e sufficiente per 950 record
   (~16 minuti per run completo)
5. **PostgreSQL locale** — nessun servizio cloud, costo zero
