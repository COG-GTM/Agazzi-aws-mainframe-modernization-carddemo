# ADR-007 — Tooling di performance (Fase 5B)

- **Stato**: proposto (Fase 5B, `migration/5b-performance`)
- **Contesto**: la Fase 5B (piano §Fase 5) chiede scenari di carico sulle API online di `openapi.yaml`
  (CC00, CAVW, CCLI, CT00/CT01/CT02, CB00, CU00) con obiettivo indicativo p95 < 200 ms a 200 utenti
  concorrenti, dati sintetici ×1.000 coerenti con i layout copybook e un harness per i job batch.
  Il perimetro 5B non può toccare il POM padre, i contratti né i moduli dei workstream.
- **Decisione**:
  - **k6** (Grafana k6 ≥ 0.54) per il carico online, non Gatling. Motivi: (1) binario singolo senza JVM
    né build Maven — non serve un modulo nel reattore né plugin nel padre; (2) scenari in JavaScript
    vicini al JSON `{action, fields}` del contratto e ai golden YAML dei workstream; (3) thresholds
    dichiarativi per tag (`http_req_duration{tx:CAVW}: p(95)<200`) che fanno fallire il run con exit
    code ≠ 0, quindi utilizzabili in CI (`modern-perf.yml`, solo `workflow_dispatch`); (4) export
    JSON del summary per il report di baseline. Gatling resta un'opzione se servisse modellare
    sessioni lunghe con feeder complessi o report HTML dettagliati: gli scenari sono piccoli e
    portabili.
  - **Generatore dati in Java 21** (`modern/perf/datagen`, build standalone con parent
    `carddemo-parent` e `relativePath`, non dichiarato nel padre): riusa i codec di Fase 0
    (`CoreCodecs`/`ZonedRecordCodec`) per scrivere i file in formato originale (zoned DISPLAY +
    overpunch, LRECL §1.3) e per i test di round-trip; `BigDecimal` ovunque, `SplittableRandom`
    seminato → output byte-identico a parità di seed.
  - **Harness batch in Bash** (`modern/perf/batch/run-batch-perf.sh`): carica i CSV con `psql \copy`
    e lancia i job col CLI Spring Batch del fat jar (`--spring.batch.job.name=<nome JCL>`), misurando
    wall-clock e record/s del dataset di input del JCL.
- **Conseguenze**: k6 va installato sull'host di test (il workflow lo scarica dalla release ufficiale
  GitHub); il datagen va buildato con `-f perf/datagen/pom.xml` dopo `install` di
  `carddemo-copybook`. Nessuna modifica al POM padre, ai contratti o ai moduli dei workstream.
