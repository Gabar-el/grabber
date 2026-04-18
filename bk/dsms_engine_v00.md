# dsms_engine_v00

## Scopo di questa versione

Questa `v00` crea una base knowledge versionata per il DSMS Engine.

Il file storico `bk/dsms_engine.md` contiene gia' il ragionamento generale del
motore. Questa versione serve a fare una cosa diversa:

- fissare i concetti stabili del motore;
- riallinearli al codice reale presente nel repository;
- distinguere cio' che e' gia' implementato da cio' che manca ancora;
- dichiarare ufficialmente che il prossimo gap architetturale e' il
  `SmoothingEngine`.

In breve:

- `dsms_engine.md` = ragionamento esplorativo e descrittivo
- `dsms_engine_v00.md` = baseline operativa e versionata

---

## Definizione del DSMS Engine

DSMS = `Diet System Modeling & Scoring`.

Il motore non deve simulare una precisione clinica falsa.
Deve invece produrre una stima:

- robusta;
- leggibile;
- coerente nel tempo;
- abbastanza spiegabile da poter essere mostrata in dashboard e assessment.

Il motore lavora su tre livelli:

1. livello teorico
2. livello realistico corretto
3. livello smoothed / dashboard-friendly

---

## Formula concettuale di base

Per una giornata:

`balance = intake - tdee`

Dove:

- `intake` = calorie introdotte
- `tdee` = dispendio energetico totale giornaliero

Interpretazione:

- bilancio negativo = deficit
- bilancio positivo = surplus
- bilancio vicino a zero = mantenimento

Scomposizione concettuale del dispendio:

`tdee = ree/bmr + neat + eat`

Nel codice corrente il sistema usa soprattutto:

- `REE` da Mifflin-St Jeor
- `TDEE` da `PAL`
- differenziazione tra giorno medio, rest day e training day

---

## Livelli ufficiali di stima

### 1. Bilancio teorico

Valore diretto derivato dai dati strutturati e dalle formule standard.

Forma concettuale:

`balanceTheoretical = intakeBase - tdeeBase`

### 2. Bilancio realistico corretto

Valore corretto con euristiche prudenti:

- intake potenzialmente sottostimato
- dispendio attivita' potenzialmente sovrastimato
- segnale storico minimo

Forma concettuale:

`balanceRealistic = intakeCorrected - tdeeCorrected`

### 3. Bilancio smoothed

Valore stabile e robusto per dashboard.

Non e' semplicemente una media.
Deve tener conto di:

- rumore del dato
- continuita' del logging
- presenza di peso/girovita
- coerenza dei giorni recenti
- outlier evidenti

Forma concettuale:

`balanceSmoothed = smooth(balanceRealistic, recentHistory, confidence)`

Questo terzo livello non e' ancora implementato come engine condiviso.

---

## Stato reale del codice al momento della v00

Il repository contiene gia' una base dominio concreta in
`lib/shared/calculations`.

### Implementato e confermato

- `ReeCalculator`
  - REE da Mifflin-St Jeor
- `TdeeCalculator`
  - TDEE medio da PAL
  - TDEE rest day
  - TDEE training day
- `BmiCalculator`
- `ActivityEnergyCalculator`
  - stima calorie da MET
  - modalita' `extra` e `gross`
- `TrendCalculator`
  - delta peso da baseline
  - delta girovita da baseline
  - moving average semplice del peso
- `ConsistencyCalculator`
  - punteggio di continuita' del monitoraggio

### Implementato fuori da `shared`, ma gia' presente nel prodotto

- primo layer di ragionamento adattivo in
  `GenerateInitialMetabolicAssessmentUseCase`
- primo layer di correzione su storico reale in
  `GenerateAdaptiveMetabolicAssessmentUseCase`

Questa seconda parte e' importante:

- il repository non e' "vuoto"
- il motore proprietario non parte da zero
- parte del `CorrectionEngine` e parte del `ConfidenceEngine` esistono gia',
  ma non sono ancora estratti come moduli condivisi

---

## Modelli dominio gia' presenti

### Profilo

`UserProfile` e' gia' il modello base per:

- sesso
- eta'
- altezza
- peso baseline
- girovita baseline
- livello attivita'

### Segnale giornaliero minimo

`DailyLogEntry` rappresenta il livello minimo di completezza del giorno:

- peso
- girovita
- meal log strutturato
- training
- nota strutturata

### Check-in reale

`CheckInEntry` aggiunge il segnale operativo del prodotto:

- `energyBalanceKcal`
- `weightKg`
- `waistCm`
- `hasStructuredMealLog`
- `hasTraining`
- `mealSections`
- `foodLogs`

Quindi il repository possiede gia' quasi tutti gli input necessari per un
motore di smoothing.

---

## Cosa manca davvero

### 1. Manca un `SmoothingEngine` condiviso

Questo e' il gap principale.

Oggi:

- il peso ha una moving average semplice
- la dashboard mostra una media grezza degli ultimi bilanci
- l'adaptive assessment usa euristiche recenti

Ma non esiste ancora un modulo che:

- prenda i bilanci recenti;
- pesi la qualita' del segnale;
- attenui outlier e oscillazioni giornaliere;
- produca un `balanceSmoothed` unico e spiegabile.

### 2. Manca un output tipizzato del motore giornaliero

Oggi `CalculationResult` e' utile per metriche singole, ma non basta per il
motore completo descritto in `dsms_engine.md`.

Serve un oggetto dedicato, ad esempio:

- `DayEvaluation`
- oppure `SmoothedBalanceResult`

con campi come:

- `intakeBaseKcal`
- `intakeCorrectedKcal`
- `tdeeBaseKcal`
- `tdeeCorrectedKcal`
- `balanceTheoreticalKcal`
- `balanceRealisticKcal`
- `balanceSmoothedKcal`
- `confidenceScore`
- `notes`

### 3. Correction e confidence non sono ancora estratti

Le euristiche esistono, ma oggi vivono soprattutto dentro use case applicativi.

Mancano quindi moduli espliciti e riusabili:

- `CorrectionEngine`
- `ConfidenceEngine`

### 4. Intake realistico ancora incompleto come motore generale

Il prodotto ha gia' un catalogo alimenti e food log strutturati.
Tuttavia non esiste ancora un `FoodEstimator` condiviso che formalizzi:

- intake base
- extra da condimenti / salse
- penalita' da pasti vaghi
- variabilita' fast food / social meals

---

## Decisione architetturale della v00

La `v00` fissa questa linea:

1. `shared/calculations` resta la base stabile del dominio numerico
2. il DSMS Engine proprietario vive sopra quella base
3. il prossimo modulo da implementare non e' un nuovo BMR o un nuovo TDEE
4. il prossimo modulo e' il `SmoothingEngine`

Conseguenza pratica:

- non va rifatto il motore da zero
- va completato il livello superiore di robustezza

---

## Direzione minima del futuro `SmoothingEngine`

La `v00` non fissa ancora la formula definitiva, ma definisce i requisiti.

Il motore dovra' usare almeno:

- media recente dei `energyBalanceKcal`
- dimensione del campione recente
- `ConsistencyCalculator`
- presenza o assenza di segnale peso
- presenza o assenza di segnale girovita
- qualita' del meal logging strutturato
- attenuazione degli outlier

Output minimo atteso:

- `smoothedBalanceKcal`
- `confidence`
- `samplesUsed`
- `windowSize`
- `signalQuality`
- `notes`

Il motore dovra' essere prudente:

- con poco dato -> avvicinarsi a zero o al realistico prudente
- con molto dato coerente -> convergere di piu' sul segnale osservato

---

## Rapporto con dashboard e adaptive assessment

### Dashboard

La dashboard oggi usa un trend recente grezzo.
Con il `SmoothingEngine`, la dashboard dovra' preferire:

- `balanceSmoothed`

e non:

- sola media aritmetica degli ultimi 7 check-in

### Adaptive assessment

L'assessment adattivo attuale contiene gia' euristiche utili:

- offset TDEE
- adattamento deficit
- confidence minima
- uso del girovita come segnale di conferma

Questa logica non va buttata.
Va progressivamente riallineata a moduli condivisi:

- `CorrectionEngine`
- `ConfidenceEngine`
- `SmoothingEngine`

---

## Roadmap minima dopo la v00

### Step 1

Introdurre un modello tipizzato del risultato giornaliero:

- `DayEvaluation` oppure `SmoothedBalanceResult`

### Step 2

Implementare `SmoothingEngine` in `shared/calculations/domain/calculators`
oppure in un layer `engine` adiacente ma sempre condiviso.

### Step 3

Sostituire in dashboard la media grezza del trend con il valore smussato.

### Step 4

Estrarre le euristiche di correzione e confidenza dagli use case applicativi in
moduli riusabili.

---

## Conclusione operativa

La `v00` registra una decisione semplice ma importante:

- la base del DSMS Engine esiste gia'
- il progetto ha gia' REE, TDEE, trend, consistency e adattamento iniziale
- il vero gap architetturale adesso non e' il "motore calorico"
- il vero gap e' il `SmoothingEngine`

Questa versione rende quindi ufficiale il perimetro:

- base numerica: presente
- euristiche adattive iniziali: presenti
- smoothing robusto e condiviso: mancante

Questa e' la prossima estensione naturale del motore.
