# Baseline prestazioni — 2026-10-19

Misure prese con gli strumenti di [README.md](README.md). **Non sono numeri di produzione**: app,
PostgreSQL e generatore di carico k6 girano sulla stessa VM (8 vCPU, 31 GiB RAM, OpenJDK 21.0.12,
PostgreSQL 16-alpine in Docker, k6 0.54.0). Servono come riferimento relativo per le iterazioni di
tuning della Fase 5.

Due build misurate:

| Build | Contenuto | Uso |
|---|---|---|
| **base** | `migration/phase-5` @ `832edd9` (solo Fase 0: `carddemo-app` + fallback security, nessun modulo di workstream) | health + datagen; è ciò che la CI di questa PR compila |
| **integ** | merge locale *usa e getta* (non pushato) di `migration/phase-5` + `1c-platform` 7882e63, `1b-datamigration` 2987757, `o1-auth` e192a28, `o2-account` 9dd5d86, `o3-card` 54d5c38, `o4-transaction` 20d32e5, `o5-useradmin` d2469bd, `b1-posting` 119c829, `b2-interest` 3befdbb, `b3-reports` 05e730b, `b4-datalifecycle` 7040a09, `b5-orchestration` 40aeeed | anteprima online/batch su dati ×1.000; da ripetere su `migration/phase-1-4` integrato |

## 1. Generatore dati ×1.000 — verificato

- `./mvnw -B -f perf/datagen/pom.xml verify`: **25 test, 0 failure** (Checkstyle + Spotless ok).
  Coprono: conteggi ×N, LRECL del piano §1.3 per ogni file, round-trip byte-per-byte con i codec
  Fase 0 (`CoreCodecs`: decode → encode di ogni record generato = riga originale), copia 0 = sample,
  unicità chiavi, tutte le FK risolte, segni overpunch (`{A..I` / `}J..R`) presenti e coerenti con il
  segno del `BigDecimal`, storico TRANSACT non duplicato dai DALYTRAN pendenti, stesso seed → file identici (SHA-256), seed diverso → file diversi,
  CSV/`load.sql` allineati allo schema V1, rifiuto di scale fuori range.
- Generazione `--scale 1000 --seed 20221231`: ~3 s, 0,8 GB.

| File | Record | LRECL |
|---|---:|---:|
| acctdata.txt | 50.000 | 300 |
| custdata.txt | 50.000 | 500 |
| carddata.txt | 50.000 | 150 |
| cardxref.txt | 50.000 | 50 |
| tcatbal.txt | 50.000 | 50 |
| dailytran.txt | 300.000 | 350 |
| transact.txt | 300.000 | 350 |

- Caricamento PostgreSQL (`psql -f load.sql`: TRUNCATE + `\copy` di 7 tabelle + ANALYZE):
  **4,8–5,1 s** per 850.000 righe (schema V1 + indici V100 della build integ).

## 2. k6 `/actuator/health` + `/actuator/info` (build base) — verificato

`k6 run -e VUS=200 -e RAMP_UP=30s -e DURATION=2m smoke-health.js`

| Metrica | Valore |
|---|---:|
| richieste | 4.513.388 (27.354 req/s) |
| `http_req_failed` | 0,00 % |
| checks | 100 % |
| p50 / p95 / p99 / max globale | 5,3 / 13,96 / 22,41 / 96,8 ms |
| p95 `HEALTH` / `INFO` | 16,2 / 10,4 ms |

Thresholds (p95 < 200 ms, p99 < 500 ms, errori < 1 %): **tutti rispettati**.

Sulla build base le API schermo non sono ancora implementate: `POST /api/v1/screens/cosgn00` → 401
(fallback security di Fase 0), quindi `online-mix.js` non è eseguibile lì.

## 3. k6 `online-mix.js` su dati ×1.000 (build integ) — anteprima

App: `SPRING_PROFILES_ACTIVE=local`, DB dedicato `carddemo_integ` caricato con `load.sql` ×1.000,
`--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration`
(vedi finding F1). Comando:
`k6 run -e BASE_URL=http://localhost:8081 -e VUS=200 -e RAMP_UP=30s -e DURATION=2m -e FIXTURES=$PWD/../../target/perf-data/k6/fixtures.json online-mix.js`

Run 1 (2 min, check solo status 200 + schermata): 67.530 richieste (400 req/s), 0,00 % errori HTTP,
100 % checks, p95 196,9 ms, p99 13,3 s (trascinato da CCLI).

Run 2, dopo la review (check di business: `nextScreen` ≠ COSGN00, nessun `fieldErrors`, CT02
"Confirm to add" → "Transaction added successfully", CB00 "Confirm to make a bill payment" →
"Payment successful" / "nothing to pay"), `RAMP_UP=20s DURATION=60s`: 30.743 richieste (308 req/s),
**0,00 % errori HTTP ma 96,98 % checks**: circa un terzo delle chiamate a CT00/CT01/CT02/CB00 viene
rimandato a COSGN00 (finding F6). Le latenze per TX restano quelle del run 1:

| TX | p50 | p90 | p95 | p99 | max | p95 < 200 ms |
|---|---:|---:|---:|---:|---:|:---:|
| CC00 sign-on | 39,0 | 113,1 | 146,7 | 212,1 | 404,9 | ✓ |
| CAVW | 6,8 | 87,4 | 119,4 | 192,7 | 339,6 | ✓ |
| CCLI (filtro account + PF8/PF7) | 168,3 | 13.190 | **13.900** | 14.990 | 17.330 | ✗ |
| CT00 (+ PF8) | 31,2 | 111,8 | 144,0 | 213,7 | 425,6 | ✓ |
| CT01 | 21,2 | 99,8 | 133,0 | 215,2 | 281,9 | ✓ |
| CT02 add + CONFIRM | 27,9 | 104,6 | 137,2 | 192,0 | 325,0 | ✓ |
| CB00 pay + CONFIRM | 22,4 | 107,4 | 145,2 | 203,2 | 362,1 | ✓ |
| CU00 (admin, scenario `admin`, PF8) | 7,0 | 88,4 | 120,5 | 193,4 | 515,8 | ✓ |

(ms; k6 esce con 99 per i threshold `checks`, CCLI e p95 globale. 285 pagamenti CB00 effettivi,
`cb00_payments`.) Le TX con check falliti (sessione rimandata a COSGN00) rispondono comunque in
fretta, quindi i loro p95 vanno ri-misurati dopo il fix F6.

### 3.1 Fase 5A — `online-mix.js` 200 VU × 5 min dopo F1/F2/F6 — verificato

App integrata in `docker compose` (immagine `carddemo-app:local` da `migration/5a-integration`,
profilo `local`, Spring Security + Spring Session JDBC, CSRF attivo), DB `carddemo` caricato con
`load.sql` ×1.000 (seed 42), k6 v0.54.0 sulla stessa macchina (8 vCPU, 31 GB, Postgres e app in
container). Comando:
`k6 run -e BASE_URL=http://localhost:8080 -e VUS=200 -e RAMP_UP=30s -e DURATION=5m -e FIXTURES=$PWD/../../target/perf-data/k6/fixtures.json online-mix.js`

263.326 richieste (763 req/s), 98.079 iterazioni, **0,00 % errori HTTP, 100 % checks** (911.367/911.367:
nessun rimbalzo a COSGN00, F6 chiuso). k6 esce comunque con 99 per i threshold di latenza.

| TX | p90 | p95 | p99 | max | p95 < 200 ms |
|---|---:|---:|---:|---:|:---:|
| CC00 sign-on | 174,3 | 393,4 | 904,1 | 3.000 | ✗ |
| CAVW | 175,3 | 362,0 | 840,2 | 2.998 | ✗ |
| CCLI (filtro account + PF8/PF7) | 162,5 | **301,7** | 798,9 | 2.429 | ✗ |
| CT00 (+ PF8) | 177,3 | 351,0 | 872,6 | 2.629 | ✗ |
| CT01 | 180,5 | 396,9 | 901,1 | 3.742 | ✗ |
| CT02 add + CONFIRM | 166,5 | 319,8 | 813,4 | 2.564 | ✗ |
| CB00 pay + CONFIRM | 173,7 | 367,4 | 853,2 | 2.282 | ✗ |
| CU00 (admin) | 176,3 | 394,1 | 865,5 | 2.266 | ✗ |
| totale | 171,8 | 351,4 | 850,9 | 3.742 | ✗ |

(ms.) CCLI passa da p95 13.900 ms a 301,7 ms (F2: path per account `card_acct_id`) e non è più
l'outlier: tutte le TX hanno ora p95 300–400 ms con p90 < 200 ms. Rispetto alla §3 il throughput
quasi raddoppia (763 vs 400 req/s) perché nessuna chiamata viene più rimbalzata e CCLI non blocca
più i VU; il p95 uniforme indica saturazione della singola macchina condivisa da k6, app e
Postgres, non un collo di bottiglia di una TX. **Il target p95 < 200 ms a 200 VU non è raggiunto su
questo ambiente**: va rimisurato su un ambiente dimensionato (RDS + task ECS separati, k6 esterno).

## 4. Batch ×1.000 — vedi tabella

`RELOAD=1` (dataset ×1.000 ricaricato prima di ogni job, ~5 s: 50.000 account/carte, 300.000
TRANSACT + 300.000 DALYTRAN), `STORAGE=db`, `TIMEOUT_S=900`.

| Build | Job | Esito | Tempo | Note |
|---|---|---|---:|---|
| `migration/phase-5` (base) | POSTTRAN, INTCALC, TRANREPT, CREASTMT, fullBatchCycle | NOT_AVAILABLE | ~4 s | moduli batch assenti: job inesistente, rc=1 |
| integ, `WEB_TYPE=none` | POSTTRAN, INTCALC, TRANREPT | NOT_AVAILABLE | ~4,4 s | il contesto non parte (F4) |
| integ, `WEB_TYPE=servlet` | POSTTRAN | **TIMEOUT** | 900 s | `STEP15` STARTED, read/write/commit = 0 (F3) |
| integ, `WEB_TYPE=servlet` | INTCALC | fermato a mano dopo ~6 min | — | `STEP15` STARTED, 0 letture; thread dump in `IntcalcTasklet.readXref` → `findByXrefAcctId` (F3) |
| integ | TRANREPT, CREASTMT, fullBatchCycle | non misurati | — | da eseguire dopo F3 |

Throughput (rec/s) non ancora disponibile: nessun job integrato completa a ×1.000 entro 15 min.
L'harness ora esce con 3 se un job non è `OK`; verificato anche su un dataset ×2 (`JOBS=NOPE` →
NOT_AVAILABLE, exit 3; cambio `SEED` → dataset rigenerato, report con il nuovo seed).

### 4.1 Fase 5A — `migration/5a-integration` dopo F3/F4 — verificato

Stessa macchina e stesso dataset ×1.000 (seed 42), `RELOAD=1`, `STORAGE=db`, app con
`spring.main.web-application-type=none` (F4). Comando:
`RESULTS=target/perf-results/batch-5a-N JOBS="..." perf/batch/run-batch-perf.sh` (CSV in
`target/perf-results/batch-5a-2` e `batch-5a-5`).

| Job | Esito | RC | Tempo | Input | rec/s | Note |
|---|---|---:|---:|---|---:|---|
| POSTTRAN | OK | 4 | 342,0 s | DALYTRAN 300.000 | 877,1 | chunk da 1.000 (F3); RC 4 = rifiuti del campione, come in COBOL |
| INTCALC | OK | 0 | 37,3 s | TCATBALF 50.000 | 1.341,3 | chunk da 500 account (F3) |
| TRANREPT | OK | 0 | 13,4 s | TRANSACT 300.000 | 22.348,0 | `startDate=2022-01-01 endDate=2022-12-31` |
| CREASTMT | **FAILED** | — | 14,3 s | TRANSACT 300.000 | — | abend `CBSTM03A: WS-CARD-TBL overflow (> 51 cards)`: limite `OCCURS 51` del COBOL, vedi sotto |
| fullBatchCycle | OK | 0 | 646,2 s | DALYTRAN 300.000 | 464,3 | catena `CLOSEFIL,POSTTRAN,INTCALC,TRANBKP,COMBTRAN,TRANREPT,OPENFIL` (senza CREASTMT) |

CREASTMT su ×1.000: `CBSTM03A` carica le carte di un cliente in `WS-CARD-TBL OCCURS 51` e le
transazioni per carta in `WS-TRAN-TBL OCCURS 10`; il generatore ×1.000 supera entrambi i limiti, che
il programma COBOL originale non gestisce. La porta Java riproduce il limite (abend) invece di
allargare la tabella, quindi CREASTMT non è misurabile su questo dataset senza una decisione di
parità (allargare le tabelle = PE nuova) o un generatore con ≤ 51 carte per cliente e ≤ 10
transazioni per carta. Sul seed (golden GnuCOBOL) CREASTMT è a 0 diff.

Il `fullBatchCycle` misurato esclude quindi CREASTMT tramite
`--carddemo.orchestration.full-cycle.jobs=...`; la catena di default resta quella completa
`CLOSEFIL → POSTTRAN → INTCALC → TRANBKP → COMBTRAN → TRANREPT → CREASTMT → OPENFIL`.

## 5. Finding per i workstream (non corretti in questa PR: fuori perimetro)

- **F1 — O1/O2, sessione JDBC**: con Spring Session JDBC (O1) attivo, `GET/POST /api/v1/screens/coactvw`
  risponde **500**: `DefaultSerializer requires a Serializable payload but received ...
  CoactvwcService$State`. Tutti gli oggetti di stato messi in sessione dai moduli online vanno resi
  `Serializable` (o serializzati JSON). Per la misura sopra la sessione è stata riportata a quella
  in-memory di Tomcat escludendo `SessionAutoConfiguration`.
- **F2 — O3, CCLI**: `CardListService` con filtro account sfoglia `CARDDAT` per chiave carta a
  blocchi (`CardBrowser.readNext`) e filtra in Java: fedele a COCRDLIC, ma O(n carte) per pagina
  (50.000 carte → p95 14,7 s). Usare il path alternato `card(card_acct_id)` (CARDAIX) quando
  `ACCTSID` è valorizzato, mantenendo l'ordine per `card_num`.
- **F3 — B1, POSTTRAN storage=db**: `PosttranStep15` è un tasklet unico (una transazione JPA per
  tutto DALYTRAN); il persistence context cresce e ogni query provoca un auto-flush che scorre tutte
  le entity gestite (`EntityEntryContext.reentrantSafeEntityEntries` nel thread dump) → costo
  quadratico. Serve chunking (commit ogni N record, piano §4) o `flush()+clear()` periodico.
  Stesso schema in **B2 INTCALC** (`IntcalcTasklet`: una `findByXrefAcctId` per record TCATBAL
  dentro un'unica transazione).
- **F4 — O1, CLI batch**: con `--spring.main.web-application-type=none` l'app non parte
  (`onlineAuthSecurityFilterChain` richiede `HttpSecurity`): le configurazioni web di O1 vanno rese
  `@ConditionalOnWebApplication`. L'harness usa quindi `WEB_TYPE=servlet --server.port=0`.
- **F5 — k6**: il cookie di sessione CC00 deve sopravvivere tra iterazioni (`noCookiesReset: true`
  in `online-mix.js`), altrimenti ogni iterazione dopo la prima riceve 401.
- **F6 — O1/O4, COMMAREA tra moduli**: dopo CC00 (anche passando da `GET /comen01`), CT00/CT01/
  CT02/CB00 rispondono `nextScreen: COSGN00` con `context: null` finché la sessione non ha visitato
  una schermata di O2/O3 (CAVW/CCLI). `ScreenSupport` di `carddemo-online-transaction` legge il
  contesto solo da `ConversationStore.CONTEXT_ATTRIBUTE`, che sign-on/menu non popolano. Il
  COMMAREA va condiviso tramite un'unica chiave di sessione (contratto O1) da tutti i moduli online.

## 6. Da eseguire quando i moduli sono integrati su `migration/phase-1-4`

1. `online-mix.js` con 200 VU / 5 min sulla build integrata (senza esclusioni di autoconfig),
   app e k6 su host separati; aggiornare questa tabella.
2. `run-batch-perf.sh` con `STORAGE=db` e `STORAGE=file` per tutti e cinque i job, dopo i fix F3/F4,
   per avere i primi tempi e rec/s.
3. Ripetere `online-mix.js` dopo F6 (checks attesi > 99 %) e dopo F2 (CCLI p95 < 200 ms).
4. Ripetere dopo ogni voce della checklist di tuning del README (indici AIX, chunk, virtual thread,
   Hikari) e registrare il delta.
