# tools/cutover — strumenti di cutover (Fase 5D)

Uso nel runbook: [docs/CUTOVER_RUNBOOK.md](../../docs/CUTOVER_RUNBOOK.md). Da `modern/`.

| Strumento | Cosa fa | Riusa |
|---|---|---|
| `cutover.sh reconcile` (`java/Reconcile.java`) | conteggi + SHA-256 per tabella vs file VSAM unload (EBCDIC CP037) o ASCII; DbSnapshot prima/dopo; report md/json | `CoreCodecs`, `ZonedRecordCodec`, `DbSnapshot` |
| `cutover.sh compare` (`java/ParallelRunCompare.java`) | confronto output batch mainframe vs Java del parallel run, report giornaliero | `GoldenJob`, `RecordComparator`, `ParityExceptions` |
| `smoke_test.py` | health, maintenance, sign-on CC00, CAVW, CT02, POSTTRAN via HTTP/CLI | contratto `openapi.yaml` |
| `run-job.sh` | lancia un job Spring Batch per nome JCL, locale o ECS Fargate | Terraform output |
| `rollback/relational-to-vsam.sh` | wrapper rollback: freeze check, `relationalToVsam`, riconciliazione, `ROLLBACK.jcl` | — |
| `decommission_inventory.py` | genera `docs/cutover/decommission-inventory.md` | `ownership.yaml`, `inventory.json` |
| `datasets.yaml` | mappa dataset VSAM ↔ tabella ↔ copybook ↔ file | — |
| `tests/selftest.sh` | self-test sul seed V1+V2 (PostgreSQL 16 in docker) | — |

Prerequisito per i tool Java: `./mvnw -B install -DskipTests` (il classpath viene calcolato una volta in
`target/cutover/classpath.txt`; oppure `CARDDEMO_CUTOVER_CP`). Password DB solo da `CARDDEMO_DB_PASSWORD`,
credenziali smoke solo da `CARDDEMO_SMOKE_USER` / `CARDDEMO_SMOKE_PASSWORD`.

```
tools/cutover/cutover.sh reconcile --db-url jdbc:postgresql://localhost:5432/carddemo --source-root ../app/data/ASCII --seed
tools/cutover/cutover.sh reconcile --db-url ... --source-root /data/T-0 --format ebcdic --datasets ACCTDATA,CARDXREF
tools/cutover/cutover.sh compare --mainframe /data/pr/2026-11-02/mainframe --java /data/pr/2026-11-02/java --jobs POSTTRAN,INTCALC,COMBTRAN,TRANREPT,CREASTMT --date 2026-11-02
tools/cutover/smoke_test.py --base-url https://carddemo.example --strict --allow-write --posttran --report smoke.json
tools/cutover/tests/selftest.sh
```

Exit code: `0` OK, `1` differenze/fallimento (no-go), `2` uso errato, `3` job non ancora registrato
nell'app (modulo non integrato), `4` lato Java non congelato (rollback).
