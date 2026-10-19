# b3-reports — Report e statement

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/b3-reports` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 2 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-batch-reports/**`
- `modern/carddemo-parity/src/test/resources/golden/b3-reports/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/b3-reports/**` (golden batch nuovi: `.../golden/b3-reports/<JOB>/`; scenari online: `.../golden/b3-reports/online/<TRANID>/*.yaml`)
- Flyway: `V400–V499` (file in `modern/carddemo-batch-reports/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
10 file, 2192 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/cbl/CBSTM03A.CBL` | 784 | batch | COSTM01, CUSTREC, CVACT01Y, CVACT03Y | CBSTM03B, CEE3ABD |
| `app/cbl/CBSTM03B.CBL` | 162 | subroutine | — | — |
| `app/cbl/CBTRN03C.cbl` | 545 | batch | CVACT03Y, CVTRA03Y, CVTRA04Y, CVTRA05Y, CVTRA07Y | CEE3ABD |
| `app/cpy/COSTM01.CPY` | 38 | — | — | — |
| `app/ctl/REPROCT.ctl` | 15 | — | — | — |
| `app/jcl/CREASTMT.JCL` | 97 | — | — | — |
| `app/jcl/TRANREPT.jcl` | 84 | — | — | — |
| `app/jcl/TXT2PDF1.JCL` | 41 | — | — | — |
| `app/proc/REPROC.prc` | 32 | — | — | — |
| `app/proc/TRANREPT.prc` | 82 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
TRANREPT (SORT + CBTRN03C + PROC REPROC/TRANREPT), CREASTMT (CBSTM03A/B), TXT2PDF1. Report a 133 colonne byte-identico (intestazioni, totali pagina/conto/generale); statement testo + HTML identici; PDF da testo con OpenPDF (confronto sul testo estratto). Implementa `ReportJobLauncherPort` (usata da O4/CR00).

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `TransactionRecord`, `CardXrefRecord`, `TransactionTypeRecord`, `TransactionCategoryRecord`, `StatementCustomerRecord`, `StatementTransactionRecord`
- Porte (`com.carddemo.contracts.port`): `RecordCodec<T>`, `BusinessClock`, `GenerationStore`, `WaitService`, `ReportJobLauncherPort`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `transaction`, `card_xref`, `transaction_type`, `transaction_category`, `customer`, `account`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): —
- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).
- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.
- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.

### Protocollo CONTRACT-CHANGE (§4.4)
Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.

## Definition of Done
- [ ] Ogni job assegnato ha un `Job` Spring Batch eseguibile da CLI (`--spring.batch.job.name=<JCL>`), step 1:1 con gli step JCL, `JobParameters` al posto di `PARM`.
- [ ] Golden test file-in/file-out verdi su tutti gli output del job (file, scarti, report) e delta DB verificato (`RecordComparator`, `DbSnapshot`).
- [ ] Test di restart (dallo step fallito) e di input vuoto/malformato; abend → exit status mappati.
- [ ] Copertura JaCoCo ≥ 80% sul modulo; ArchUnit verde; nessun file toccato fuori dal perimetro.
- [ ] `ReportJobLauncherPort` implementato come bean Spring (sostituisce `UnavailableReportJobLauncher` di Fase 0 grazie a `@ConditionalOnMissingBean`).
- [ ] Golden specifici: TRANREPT, CREASTMT.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
