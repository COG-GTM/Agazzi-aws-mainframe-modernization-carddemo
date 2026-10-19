# b4-datalifecycle — Data lifecycle: caricamento, export/import, utility

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/b4-datalifecycle` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 2 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-batch-datalifecycle/**`
- `modern/carddemo-parity/src/test/resources/golden/b4-datalifecycle/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/b4-datalifecycle/**` (golden batch nuovi: `.../golden/b4-datalifecycle/<JOB>/`; scenari online: `.../golden/b4-datalifecycle/online/<TRANID>/*.yaml`)
- Flyway: `V500–V599` (file in `modern/carddemo-batch-datalifecycle/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
25 file, 3405 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/cbl/CBACT01C.cbl` | 358 | batch | CODATECN, CVACT01Y | CEE3ABD, COBDATFT |
| `app/cbl/CBACT02C.cbl` | 129 | batch | CVACT02Y | CEE3ABD |
| `app/cbl/CBACT03C.cbl` | 130 | batch | CVACT03Y | CEE3ABD |
| `app/cbl/CBCUS01C.cbl` | 130 | batch | CVCUS01Y | CEE3ABD |
| `app/cbl/CBEXPORT.cbl` | 396 | batch | CVACT01Y, CVACT02Y, CVACT03Y, CVCUS01Y, CVEXPORT, CVTRA05Y | CEE3ABD |
| `app/cbl/CBIMPORT.cbl` | 337 | batch | CVACT01Y, CVACT02Y, CVACT03Y, CVCUS01Y, CVEXPORT, CVTRA05Y | CEE3ABD |
| `app/jcl/ACCTFILE.jcl` | 65 | — | — | — |
| `app/jcl/CARDFILE.jcl` | 128 | — | — | — |
| `app/jcl/CBEXPORT.jcl` | 72 | — | — | — |
| `app/jcl/CBIMPORT.jcl` | 68 | — | — | — |
| `app/jcl/CUSTFILE.jcl` | 84 | — | — | — |
| `app/jcl/DEFCUST.jcl` | 47 | — | — | — |
| `app/jcl/DEFGDGB.jcl` | 63 | — | — | — |
| `app/jcl/DEFGDGD.jcl` | 94 | — | — | — |
| `app/jcl/DUSRSECJ.jcl` | 92 | — | — | — |
| `app/jcl/ESDSRRDS.jcl` | 124 | — | — | — |
| `app/jcl/READACCT.jcl` | 50 | — | — | — |
| `app/jcl/READCARD.jcl` | 31 | — | — | — |
| `app/jcl/READCUST.jcl` | 30 | — | — | — |
| `app/jcl/READXREF.jcl` | 31 | — | — | — |
| `app/jcl/REPTFILE.jcl` | 32 | — | — | — |
| `app/jcl/TRANCATG.jcl` | 65 | — | — | — |
| `app/jcl/TRANFILE.jcl` | 125 | — | — | — |
| `app/jcl/TRANTYPE.jcl` | 65 | — | — | — |
| `app/jcl/XREFFILE.jcl` | 106 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
ACCTFILE, CARDFILE, XREFFILE, CUSTFILE, TRANFILE, TRANTYPE, TRANCATG, DUSRSECJ, DEFCUST, REPTFILE, ESDSRRDS, DEFGDGB/D, READACCT/CARD/XREF/CUST (CBACT01C–03C, CBCUS01C), CBEXPORT, CBIMPORT. Riusa (via porte) i job di 1B per il refresh; CBEXPORT/CBIMPORT sul tipo sealed `ExportRecord` di 1A (usa il codec minimo finché 1A non mergia); READACCT produce i 3 formati PSCOMP, ARRYPS, VBPS identici; GDG via `GenerationStore`.

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: record core di `com.carddemo.contracts.record`
- Porte (`com.carddemo.contracts.port`): `RecordCodec<T>`, `BusinessClock`, `GenerationStore`, `WaitService`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `account`, `card`, `card_xref`, `customer`, `transaction`, `transaction_type`, `transaction_category`, `app_user`, `usrsec_esds`, `usrsec_rrds`
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
- [ ] Golden specifici: READACCT, nuovi golden READCARD/READXREF/READCUST/CBEXPORT/CBIMPORT (adattamenti GnuCOBOL solo nel harness).
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
