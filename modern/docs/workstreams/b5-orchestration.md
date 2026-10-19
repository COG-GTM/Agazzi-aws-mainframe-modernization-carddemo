# b5-orchestration — Orchestrazione batch e scheduler

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/b5-orchestration` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 2 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-batch-orchestration/**`
- `modern/carddemo-parity/src/test/resources/golden/b5-orchestration/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/b5-orchestration/**` (golden batch nuovi: `.../golden/b5-orchestration/<JOB>/`; scenari online: `.../golden/b5-orchestration/online/<TRANID>/*.yaml`)
- Flyway: `V600–V699` (file in `modern/carddemo-batch-orchestration/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
10 file, 1042 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/cbl/COBSWAIT.cbl` | 13 | batch | — | MVSWAIT |
| `app/jcl/CBADMCDJ.jcl` | 167 | — | — | — |
| `app/jcl/CLOSEFIL.jcl` | 34 | — | — | — |
| `app/jcl/FTPJCL.JCL` | 42 | — | — | — |
| `app/jcl/INTRDRJ1.JCL` | 19 | — | — | — |
| `app/jcl/INTRDRJ2.JCL` | 14 | — | — | — |
| `app/jcl/OPENFIL.jcl` | 34 | — | — | — |
| `app/jcl/WAITSTEP.jcl` | 27 | — | — | — |
| `app/scheduler/CardDemo.ca7` | 571 | — | — | — |
| `app/scheduler/CardDemo.controlm` | 93 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
Flussi Control-M DAILY/WEEKLY/MONTHLY e CA-7 → `Flow` Spring Batch con le stesse condizioni IN/OUT; CLOSEFIL/OPENFIL → `MaintenanceModePort`; WAITSTEP (COBSWAIT) → `WaitService`; INTRDRJ1/2 (submit asincrono) → `JobLauncher`; FTPJCL → SFTP/S3 (credenziali da secret manager, mai nel codice); `scripts/run_full_batch.sh` → job `fullBatchCycle` usato in Fase 5. I job degli altri workstream si invocano per nome (`JobRegistry`), mai per dipendenza Maven.

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: —
- Porte (`com.carddemo.contracts.port`): `RecordCodec<T>`, `BusinessClock`, `GenerationStore`, `WaitService`, `MaintenanceModePort`, `ReportJobLauncherPort`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `BATCH_* (Spring Batch, V3)`
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
- [ ] `fullBatchCycle` esegue la catena POSTTRAN → INTCALC → COMBTRAN → TRANREPT → CREASTMT nell'ordine dello scheduler.
- [ ] Golden specifici: catena completa (POSTTRAN, INTCALC, COMBTRAN, TRANREPT, CREASTMT) come smoke di orchestrazione.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
