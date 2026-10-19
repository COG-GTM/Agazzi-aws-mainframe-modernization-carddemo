# Fase 5B — Prestazioni

Strumenti per misurare CardDemo Java 21 su volumi realistici (piano `docs/MIGRATION_PLAN_JAVA21.md`,
Fase 5B; scelta del tooling in [ADR-007](../adr/007-performance-tooling.md)). Baseline misurata:
[baseline.md](baseline.md).

| Pezzo | Percorso | Cosa fa |
|---|---|---|
| Generatore dati | `modern/perf/datagen` | ×N (default ×1.000) deterministico del sample `app/data/ASCII` |
| Carico online | `modern/perf/k6` | k6: `smoke-health.js`, `online-mix.js` (CC00, CAVW, CCLI, CT00, CT01, CT02, CB00, CU00) |
| Batch | `modern/perf/batch/run-batch-perf.sh` | carica i dati e cronometra `POSTTRAN`, `INTCALC`, `TRANREPT`, `CREASTMT`, `fullBatchCycle` |
| CI on-demand | `.github/workflows/modern-perf.yml` | solo `workflow_dispatch`, artifact `perf-results` |

## 1. Ambiente

Stesso stack del `modern/docker-compose.yml` (PostgreSQL 16, LocalStack SQS, app su `:8080`, profilo
`local` = Flyway V1–V3 + seed V2). Per misure ripetibili:

```bash
cd modern
docker compose up -d postgres localstack            # DB + SQS
./mvnw -B -q -pl carddemo-app -am package -DskipTests
SPRING_PROFILES_ACTIVE=local java -jar carddemo-app/target/carddemo-app-exec.jar &   # oppure: docker compose up -d app
curl -s localhost:8080/actuator/health              # {"status":"UP",...}
```

Tooling richiesto sull'host di carico: Java 21, `psql` (client PostgreSQL), k6 ≥ 0.54
(`curl -sSL https://github.com/grafana/k6/releases/download/v0.54.0/k6-v0.54.0-linux-amd64.tar.gz | tar xz`).
Generatore di carico e app su macchine diverse per misure "vere"; sulla stessa macchina i numeri
sono una baseline relativa (vedi `baseline.md`). Le credenziali di DB e utenti sono quelle demo del
sample (`docker-compose.yml`, `DUSRSECJ`): non puntare questi script a sistemi reali.

## 2. Dati sintetici (`modern/perf/datagen`)

```bash
cd modern
./mvnw -B -q install -pl carddemo-copybook -am -DskipTests      # codec Fase 0
./mvnw -B -f perf/datagen/pom.xml verify                        # build + 25 test (round-trip incluso)
java -jar perf/datagen/target/carddemo-perf-datagen-0.1.0-SNAPSHOT.jar \
  --sample ../app/data/ASCII --out target/perf-data --scale 1000 --seed 20221231
```

Output in `target/perf-data/` (×1.000 ≈ 0,8 GB, ~3 s):

| Cartella | Contenuto |
|---|---|
| `ascii/` | `acctdata.txt` 50.000×300, `custdata.txt` 50.000×500, `carddata.txt` 50.000×150, `cardxref.txt` 50.000×50, `tcatbal.txt` 50.000×50, `dailytran.txt` 300.000×350, `transact.txt` 300.000×350 (+ `discgrp/trantype/trancatg` invariati). Zoned DISPLAY + overpunch via `CoreCodecs`, LRECL §1.3, un record per riga |
| `datasets/` | gli stessi file con i nomi DSN usati dai job in `storage=file` (`ACCTDATA.dat`, `DALYTRAN.dat`, `TRANSACT.dat`, …) |
| `csv/` + `load.sql` | CSV per tabella dello schema V1 + script `psql` (`TRUNCATE` + `\copy` + `ANALYZE`): `cd target/perf-data && psql -f load.sql` |
| `k6/fixtures.json` | 2.000 account/carta/cliente + 2.000 TRAN-ID esistenti per gli scenari k6 |

Regole: la copia 0 è il sample identico; le copie 1..N-1 rimappano le chiavi con funzioni pure
(`ACCT-ID + k·10⁶`, `CUST-ID + k·10⁴`, `CARD-NUM` = prefisso a 4 cifre non usato dal sample + ultime 12
cifre) così tutte le FK (card→account, xref→card/customer/account, tcatbal→account,
transaction/dalytran→xref) restano coerenti; importi, limiti, CVV, SSN, FICO e nomi sono perturbati
con `SplittableRandom(seed, k)` (stesso seed → stessi byte). TRANSACT contiene storico del ciclo
precedente (stessa carta, importo ridisegnato, timestamp −35 giorni) con TRAN-ID `1..N`, così POSTTRAN
non duplica i DALYTRAN pendenti; DALYTRAN-ID `5·10¹⁵+n` (nessuna collisione con i nuovi id generati
da CT02/CB00/POSTTRAN). Se `DATA` contiene un dataset con scale/seed diversi da quelli richiesti,
`run-batch-perf.sh` lo rigenera (manifest in `k6/fixtures.json`).

## 3. Carico online (k6)

```bash
cd modern/perf/k6
k6 run -e VUS=200 -e DURATION=5m smoke-health.js
k6 run -e BASE_URL=http://localhost:8080 -e VUS=200 -e DURATION=5m \
       -e FIXTURES=$PWD/../../target/perf-data/k6/fixtures.json \
       --summary-export ../../target/perf-results/k6-online.json online-mix.js
```

Parametri (`-e` o env): `BASE_URL`, `VUS` (200), `RAMP_UP` (1m), `DURATION` (5m), `P95_MS` (200),
`P99_MS` (500), `MAX_ERROR_RATE` (0.01), `THINK_TIME_S` (0), `MIX`
(`CAVW:20,CCLI:15,CT00:15,CT01:15,CT02:10,CB00:10,CU00:5,CC00:10`), `SIGNON_EVERY` (50), `USERS`,
`ADMINS`, `CARDDEMO_PASSWORD`, `FIXTURES` (path assoluto).

Ogni VU fa sign-on CC00 (`POST /api/v1/screens/cosgn00`, cookie di sessione nel cookie jar del VU) e
poi cicla sul mix: GET = primo SEND della mappa, POST = input terminale `{action, fields}`.
CCLI pagina con PF8/PF7, CT00 con PF8, CT02 e CB00 completano con `CONFIRM=Y` (scrivono TRANSACT),
CU00 gira in uno scenario `admin` separato con `VUS·w(CU00)/Σw` VU (default 10 su 200; almeno 1
admin e 1 user, quindi `VUS>=2` se `MIX` contiene CU00 e altre transazioni).
Check di business oltre a status 200 + schermata attesa: nessun `fieldErrors` su CAVW/CCLI/CT00/CT01/CT02,
CT02 → "Transaction added successfully", CB00 → "Payment successful" o "You have nothing to pay"
(account già saldato; i pagamenti reali sono contati in `cb00_payments`). Thresholds: globali (`http_req_failed`, `checks`, p95/p99) e
per transazione (`http_req_duration{tx:CAVW}` …): un threshold violato → exit code 99.

## 4. Batch ×1.000

```bash
cd modern
perf/batch/run-batch-perf.sh                                   # tutti i job, storage=db
JOBS="POSTTRAN" STORAGE=file JAVA_OPTS="-Xmx2g" perf/batch/run-batch-perf.sh
```

Lo script genera i dati se mancano, li carica (`load.sql`), poi per ogni job ricarica il dataset
(`RELOAD=1`, tempi isolati; `RELOAD=0` = catena) e lancia
`java -jar carddemo-app-exec.jar --spring.batch.job.name=<JOB> ... storage=<db|file> datasetDir=<dir> run.id=<ns>`.
Esito da exit code + `batch_job_execution.status` (lo script esce con 3 se un job non è `OK`, dopo aver
scritto il report); output in `target/perf-results/batch-<ts>/`
(`batch-results.csv|md`, log per job). Il throughput è record del DD di input del JCL
(DALYTRAN per POSTTRAN/fullBatchCycle, TCATBALF per INTCALC, TRANSACT per TRANREPT/CREASTMT) / tempo
wall-clock del JVM (avvio Spring incluso, ~2–5 s).

## 5. Metriche da raccogliere

- **Online** (k6 summary): p50/p90/p95/p99/max di `http_req_duration` globale e per `tx`, `http_reqs/s`,
  `http_req_failed`, `checks`; lato app `/actuator/metrics/http.server.requests`,
  `hikaricp.connections.active|pending|usage`, `jvm.threads.live`, `jvm.gc.pause`, `process.cpu.usage`.
- **Batch**: elapsed e record/s per job, durata per step (`batch_step_execution`: `read_count`,
  `write_count`, `commit_count`, `start_time`/`end_time`), RSS/heap del JVM, tempo di `load.sql`.
- **DB**: `pg_stat_statements` (top query per `total_exec_time`), `pg_stat_user_tables`
  (`seq_scan` vs `idx_scan` su `transaction`, `card`, `card_xref`), lock wait, checkpoint.

## 6. Checklist di tuning

- [ ] **Indici AIX**: equivalenti dei path alternati VSAM — `card(card_acct_id)` (CARDAIX),
  `card_xref(xref_acct_id)` (CXACAIX), `transaction(tran_proc_ts)` (TRANSACT AIX); verificare con
  `EXPLAIN` che CCLI/CAVW/CB00 non facciano seq scan a 50.000 carte; aggiungere
  `transaction(tran_card_num)` se CT00/CREASTMT filtrano per carta (Flyway V9000+, Fase 5).
- [ ] **Chunk / batch size**: chunk Spring Batch 500–2.000 per POSTTRAN/INTCALC/TRANREPT,
  `spring.jpa.properties.hibernate.jdbc.batch_size` = chunk, `order_inserts/order_updates=true`,
  `reWriteBatchedInserts=true` sul JDBC URL PostgreSQL; fetch size del reader (`JdbcCursorItemReader`)
  ≥ chunk.
- [ ] **Virtual thread**: `spring.threads.virtual.enabled=true` (Tomcat + `@Async`) per l'online;
  con virtual thread il collo di bottiglia diventa il pool JDBC → dimensionarlo prima.
- [ ] **Pool Hikari**: `spring.datasource.hikari.maximum-pool-size` ≈ `(core DB × 2) + dischi`
  (10–30), `minimum-idle` = max per il carico costante, `connection-timeout` < p99 obiettivo;
  monitorare `hikaricp.connections.pending` > 0.
- [ ] **JVM**: `-XX:+UseZGC -XX:+ZGenerational` o G1 con `-Xmx` fisso; `-XX:MaxRAMPercentage=75` nel
  container.
- [ ] **Sessione**: store JDBC (Spring Session, O1) = 1–2 query in più per richiesta; valutare cache.
- [ ] **PostgreSQL**: `shared_buffers` 25% RAM, `work_mem` per sort TRANREPT/CREASTMT,
  `synchronous_commit=off` solo per run batch di test.
