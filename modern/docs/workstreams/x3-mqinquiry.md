# x3-mqinquiry — Estensione inquiry via MQ

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/x3-mqinquiry` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 4 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-ext-mqinquiry/**`
- `modern/carddemo-parity/src/test/resources/golden/x3-mqinquiry/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/x3-mqinquiry/**` (golden batch nuovi: `.../golden/x3-mqinquiry/<JOB>/`; scenari online: `.../golden/x3-mqinquiry/online/<TRANID>/*.yaml`)
- Flyway: `V2200–V2299` (file in `modern/carddemo-ext-mqinquiry/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
4 file, 1329 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/app-vsam-mq/README.md` | 144 | — | — | — |
| `app/app-vsam-mq/cbl/COACCT01.cbl` | 596 | online+mq | 034400, CMQGMOV, CMQMDV, CMQODV, CMQPMOV, CMQTML, CMQV, CVACT01Y, REPLACING | MQCLOSE, MQGET, MQOPEN, MQPUT |
| `app/app-vsam-mq/cbl/CODATE01.cbl` | 507 | online+mq | 034400, CMQGMOV, CMQMDV, CMQODV, CMQPMOV, CMQTML, CMQV, REPLACING | MQCLOSE, MQGET, MQOPEN, MQPUT |
| `app/app-vsam-mq/csd/CRDDEMOM.csd` | 41 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
`app/app-vsam-mq` (CODATE01, COACCT01): CDRD/CDRA → listener SQS request/reply (data di sistema, dettaglio conto) via `MessageChannelPort` con lo stesso formato messaggio; attivabile con `carddemo.extensions.mqinquiry.enabled=true` (default off).

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `AccountRecord`
- Porte (`com.carddemo.contracts.port`): `MessageChannelPort`, `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `account`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/ext/mqinquiry/system-date` — CDRD (CODATE01) system date inquiry, REST twin of the SQS request/reply (optional) _(opzionale, estensione)_
  - `GET /api/v1/ext/mqinquiry/accounts/{acctId}` — CDRA (COACCT01) account inquiry, REST twin of the SQS request/reply (optional) _(opzionale, estensione)_
- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).
- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.
- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.

### Protocollo CONTRACT-CHANGE (§4.4)
Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.

## Definition of Done
- [ ] Test di integrazione LocalStack SQS; formato di risposta identico byte per byte.
- [ ] Regressione app base con flag ON e OFF.
- [ ] Copertura ≥ 80%; ArchUnit verde.
- [ ] Golden specifici: nuovi golden messaggi CDRD/CDRA.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
