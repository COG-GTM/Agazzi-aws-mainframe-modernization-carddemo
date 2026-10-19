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

Totale: 67.530 richieste (400 req/s, 31.623 iterazioni), **0,00 % errori, 100 % checks** (status 200
+ schermata attesa), p50 23,6 ms, p95 **196,9 ms**, p99 13,3 s (trascinato da CCLI).

| TX | p50 | p90 | p95 | p99 | max | p95 < 200 ms |
|---|---:|---:|---:|---:|---:|:---:|
| CC00 sign-on | 47,2 | 133,8 | 167,5 | 249,4 | 376,7 | ✓ |
| CAVW | 18,1 | 100,5 | 134,1 | 199,4 | 344,2 | ✓ |
| CCLI (filtro account + PF8/PF7) | 2.053 | 13.944 | **14.731** | 16.116 | 18.221 | ✗ |
| CT00 (+ PF8) | 44,1 | 133,0 | 162,8 | 229,2 | 409,2 | ✓ |
| CT01 | 31,3 | 119,1 | 153,3 | 221,0 | 344,0 | ✓ |
| CT02 add + CONFIRM | 34,7 | 124,2 | 159,7 | 241,2 | 435,4 | ✓ |
| CB00 pay + CONFIRM | 32,4 | 123,0 | 156,1 | 214,3 | 364,2 | ✓ |
| CU00 (admin, PF8) | 19,2 | 101,2 | 135,8 | 212,0 | 510,0 | ✓ |

(ms; k6 esce con 99 per i threshold CCLI e p99 globale.)

## 4. Batch ×1.000 — vedi tabella

BATCH_RESULTS_PLACEHOLDER

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
- **F4 — O1, CLI batch**: con `--spring.main.web-application-type=none` l'app non parte
  (`onlineAuthSecurityFilterChain` richiede `HttpSecurity`): le configurazioni web di O1 vanno rese
  `@ConditionalOnWebApplication`. L'harness usa quindi `WEB_TYPE=servlet --server.port=0`.
- **F5 — k6**: il cookie di sessione CC00 deve sopravvivere tra iterazioni (`noCookiesReset: true`
  in `online-mix.js`), altrimenti ogni iterazione dopo la prima riceve 401.

## 6. Da eseguire quando i moduli sono integrati su `migration/phase-1-4`

1. `online-mix.js` con 200 VU / 5 min sulla build integrata (senza esclusioni di autoconfig),
   app e k6 su host separati; aggiornare questa tabella.
2. `run-batch-perf.sh` con `STORAGE=db` e `STORAGE=file` per tutti e cinque i job, dopo i fix F3/F4.
3. Ripetere dopo ogni voce della checklist di tuning del README (indici AIX, chunk, virtual thread,
   Hikari) e registrare il delta.
