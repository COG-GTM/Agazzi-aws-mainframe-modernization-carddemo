# 1b-datamigration — Schema relazionale e migrazione dati

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/1b-datamigration` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 1 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-datamigration/**`
- `modern/carddemo-parity/src/test/resources/golden/1b-datamigration/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/1b-datamigration/**` (golden batch nuovi: `.../golden/1b-datamigration/<JOB>/`; scenari online: `.../golden/1b-datamigration/online/<TRANID>/*.yaml`)
- Flyway: `V100–V199` (file in `modern/carddemo-datamigration/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
23 file, 4611 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/catlg/LISTCAT.txt` | 3956 | — | — | — |
| `app/data/ASCII/acctdata.txt` | 50 | — | — | — |
| `app/data/ASCII/carddata.txt` | 50 | — | — | — |
| `app/data/ASCII/cardxref.txt` | 50 | — | — | — |
| `app/data/ASCII/custdata.txt` | 50 | — | — | — |
| `app/data/ASCII/dailytran.txt` | 300 | — | — | — |
| `app/data/ASCII/discgrp.txt` | 51 | — | — | — |
| `app/data/ASCII/tcatbal.txt` | 50 | — | — | — |
| `app/data/ASCII/trancatg.txt` | 18 | — | — | — |
| `app/data/ASCII/trantype.txt` | 7 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.ACCDATA.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.CARDDATA.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.CARDXREF.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.CUSTDATA.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS.INIT` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.DISCGRP.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.EXPORT.DATA.PS` | 17 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.TCATBALF.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANCATG.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.TRANTYPE.PS` | 1 | — | — | — |
| `app/data/EBCDIC/AWS.M2.CARDDEMO.USRSEC.PS` | 1 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
Pipeline Spring Batch `vsamToRelational` (PS/export VSAM EBCDIC o ASCII → decodifica → validazione chiavi duplicate/FK orfane/date invalide → upsert idempotente per PK → report di riconciliazione) e job inverso `relationalToVsam` (rollback e confronti di parità). Indici/vincoli finali, tabelle ESDS/RRDS, migrazione password USRSEC → BCrypt (`{bcrypt}`) come migrazione separata e reversibile. CLI `migrate-data` (`java -jar … --job=vsamToRelational --source=ebcdic`).

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: record core di `com.carddemo.contracts.record` + entity/repository di `com.carddemo.domain`
- Porte (`com.carddemo.contracts.port`): `RecordCodec<T>`, `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `tutte le tabelle di V1`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): —
- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).
- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.
- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.

### Protocollo CONTRACT-CHANGE (§4.4)
Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.

## Definition of Done
- [ ] Conteggi e checksum (`ACCT-CURR-BAL`, `TRAN-AMT`, `TRAN-CAT-BAL`, hash per colonna) identici fra sorgente e DB per tutti i dataset; `reconciliation.md` generato.
- [ ] `relationalToVsam` riproduce i file `app/data/ASCII` byte-identici; riesecuzione idempotente.
- [ ] Test Testcontainers PostgreSQL 16 (profilo `it`).
- [ ] Golden specifici: `app/data/ASCII/*` (round-trip DB).
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
