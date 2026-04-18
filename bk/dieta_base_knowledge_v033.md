# dieta_base_knowledge_v033

## Origine di questa versione

Questa versione aggiorna `dieta_base_knowledge_v032`.

La `v032` aveva chiuso:

- il dominio `CheckInFoodLog` e il bilancio kcal derivato da alimenti strutturati;
- la persistenza `Isar` del `checkin` con rigenerazione degli schema `g.dart`;
- la prima versione del picker catalogo con selezione multipla e dialog quantita';
- la decisione architetturale: DSMS trasforma il check-in da input manuale a bilancio derivato.

La `v033` registra un passo diverso: nessuna nuova logica di dominio, ma una tranche significativa di miglioramento della leggibilita' e usabilita' della dashboard.

In sintesi:

- `v032` = primo ciclo completo: catalogo → grammi → kcal → bilancio derivato
- `v033` = dashboard leggibile: grafici scrollabili, KPI card allineate

---

## Stato reale consolidato al 13 aprile 2026

### 1. Soluzione B per i grafici della dashboard — completata

Il problema originale era visibile sul device: con molti check-in, le barre del `DailyBalanceChart` diventavano troppo strette e le date sulle x si sovrapponevano, rendendo il grafico illeggibile e impossibile da toccare per il drill-down.

Sono state valutate cinque strategie alternative. L'utente ha scelto la **Soluzione B**:

- barra a larghezza fissa (`barWidth = 28px`);
- pannello Y fisso a sinistra (`_yAxisWidth = 58px`) che non scrolla;
- area destra scrollabile orizzontalmente con `SingleChildScrollView`;
- apertura automatica sull'ultimo dato (scroll-to-end via `addPostFrameCallback`);
- stride X adattivo: 1 etichetta ogni ~84px (`math.max(1, (84.0 / barWidth).ceil())`).

La stessa soluzione e' stata estesa a `TimeSeriesLineChart`, che serve sia il grafico
`Deficit cumulativo` sia il grafico `Girovita nel tempo`:

- spacing fisso tra punti: `pointSpacing = 40px`;
- pannello Y fisso: `_LineYAxisPainter` (separato da `_YAxisPainter` del bar chart);
- `_LineChartPainter` riscritto senza label Y, con `_pointX(i) = pointSpacing * i + pointSpacing / 2`;
- rilevamento del punto attivo via offset viewport + scroll offset del controller;
- tooltip posizionato nel canvas scrollabile (segue il punto attivo).

---

### 2. Bug critico risolto: LateInitializationError sul ScrollController

Durante il primo hot-reload dopo l'introduzione del `ScrollController` in `_TimeSeriesLineChartState`, l'app crashava con:

```
LateInitializationError: Field '_scrollController@...' has not been initialized.
```

**Causa**: i campi `late final` vengono inizializzati in `initState`. Con hot-reload, Flutter riutilizza gli oggetti state gia' montati senza richiamare `initState`, quindi il campo restava non inizializzato mentre `build` veniva gia' eseguito.

**Fix**: spostare l'inizializzazione al livello del campo:

```dart
// prima (fragile in hot-reload)
late final ScrollController _scrollController;
void initState() { _scrollController = ScrollController(); ... }

// dopo (robusto sempre)
final ScrollController _scrollController = ScrollController();
void initState() { /* solo addPostFrameCallback */ ... }
```

Lo stesso fix e' stato applicato anche a `_DailyBalanceChartState` per coerenza.

**Regola acquisita**: i `ScrollController` (e in generale i controller Flutter) vanno sempre inizializzati a livello di dichiarazione del campo, non in `initState`, per evitare LateInitializationError in hot-reload.

---

### 3. KPI card della dashboard — fix altezze e font

Due problemi visivi sulle `DashboardKpiCard` nel carosello orizzontale:

#### 3a. Visibility index piu' bassa delle altre card

**Causa**: il valore era una sola riga (`'100%'`), mentre le altre card hanno valori con `\n` esplicito (`'-500\nkcal'`), che aggiungono una seconda riga al testo `headlineSmall`.

**Fix**: aggiunto `\n` al valore: `'${rangeMetrics.visibilityIndex}%\n'`.

#### 3b. Phase card — CONSOLIDATION troppo grande e card piu' bassa

**Causa duplice**:
1. "CONSOLIDATION" (13 caratteri) a `headlineSmall` (24sp) non entrava nei 144px utili della card (176px larghezza − 16px padding × 2), causando un'a capo involontaria.
2. Una volta ridotto il font, la card diventava piu' bassa delle altre.

**Fix**:

- Aggiunto `valueFontSize: double?` opzionale alla `DashboardKpiCard`; se passato, sovrascrive il `fontSize` nel `copyWith` del testo valore.
- Phase card usa `valueFontSize: 16.0`.
- Per pareggiare l'altezza senza hardcodare pixels: a `headlineSmall` (24sp) con `height: 1.1`, due righe occupano `2 × 24 × 1.1 = 52.8px`; a 16sp tre righe occupano `3 × 16 × 1.1 = 52.8px`. Quindi il valore Phase usa `'${rangeMetrics.phase.label}\n\n'` (due newline) per raggiungere tre righe a 16sp, pareggiando esattamente l'altezza delle altre card a due righe a 24sp.

---

### 4. Architettura dei grafici dopo la v033

Le classi custom painter ora seguono questa separazione netta:

| Classe | Ruolo |
|---|---|
| `_YAxisPainter` | Pannello Y fisso per `DailyBalanceChart` (asse simmetrico ± axisMax) |
| `_LineYAxisPainter` | Pannello Y fisso per `TimeSeriesLineChart` (asse minY..maxY con 5 tick) |
| `_DailyBalancePainter` | Canvas scrollabile del bar chart (grid, barre logica, media mobile, target, label X) |
| `_LineChartPainter` | Canvas scrollabile del line chart (grid, linea cubica, dot, selezione, label X) |

La funzione `_lineChartLabelStride` e' stata rimossa (sostituita dallo stride fisso `84px / pointSpacing`).
La funzione `_labelStride` (vecchia, per bar chart) e' stata rimossa in quanto non piu' referenziata.
La funzione `_formatLineYLabel` e' stata aggiunta come helper top-level per `_LineYAxisPainter`.

---

### 5. Roadmap aggiornata dopo la v033

Ordine corretto dei prossimi passi:

1. ~~rigenerare i `g.dart` del `checkin`~~ — fatto nella v032
2. ~~verificare l'avvio reale dell'app su device~~ — verificato
3. validare salvataggio e ricarica dei `foodLogs` su device reale
4. confermare che dashboard e archivio leggano correttamente i nuovi check-in strutturati
5. spostare la cartella import/export in una directory Android visibile (`Download/DSMS`) — ancora aperto
6. solo dopo, riprendere l'espansione dei provider remoti e la logica cataloghi

---

## Decisione di prodotto registrata nella v033

La direzione tecnica della `v032` e' confermata e ora anche visivamente validata sul device:

## **DSMS e' un'app operativa, non un prototipo. La dashboard deve essere leggibile e usabile in ogni condizione di dati.**

La `v033` chiude la parte di presentazione della dashboard.

Il prossimo ciclo riprende dalla validazione end-to-end del flusso:
catalogo → grammi → kcal → salvataggio → lettura → dashboard.
