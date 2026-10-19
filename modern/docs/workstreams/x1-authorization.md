# x1-authorization — Estensione autorizzazioni IMS/DB2/MQ

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/x1-authorization` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 4 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-ext-authorization/**`
- `modern/carddemo-parity/src/test/resources/golden/x1-authorization/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/x1-authorization/**` (golden batch nuovi: `.../golden/x1-authorization/<JOB>/`; scenari online: `.../golden/x1-authorization/online/<TRANID>/*.yaml`)
- Flyway: `V2000–V2099` (file in `modern/carddemo-ext-authorization/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
40 file, 7610 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/app-authorization-ims-db2-mq/README.md` | 358 | — | — | — |
| `app/app-authorization-ims-db2-mq/bms/COPAU00.bms` | 515 | — | — | — |
| `app/app-authorization-ims-db2-mq/bms/COPAU01.bms` | 294 | — | — | — |
| `app/app-authorization-ims-db2-mq/cbl/CBPAUP0C.cbl` | 266 | batch+ims | CIPAUDTY, CIPAUSMY | — |
| `app/app-authorization-ims-db2-mq/cbl/COPAUA0C.cbl` | 771 | online+ims+mq | CCPAUERY, CCPAURLY, CCPAURQY, CIPAUDTY, CIPAUSMY, CMQGMOV, CMQMDV, CMQODV, CMQPMOV, CMQTML, CMQV, CVACT01Y, CVACT03Y, CVCUS01Y | MQCLOSE, MQGET, MQOPEN, MQPUT1 |
| `app/app-authorization-ims-db2-mq/cbl/COPAUS0C.cbl` | 792 | online+ims | CIPAUDTY, CIPAUSMY, COCOM01Y, COPAU00, COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CVACT01Y, CVACT02Y, CVACT03Y, CVCUS01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, COMEN01C, COPAUS1C |
| `app/app-authorization-ims-db2-mq/cbl/COPAUS1C.cbl` | 461 | online+ims | CIPAUDTY, CIPAUSMY, COCOM01Y, COPAU01, COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, COPAUS0C, COPAUS2C |
| `app/app-authorization-ims-db2-mq/cbl/COPAUS2C.cbl` | 201 | online+db2 | CIPAUDTY | — |
| `app/app-authorization-ims-db2-mq/cbl/DBUNLDGS.CBL` | 198 | subroutine+ims | CIPAUDTY, CIPAUSMY, IMSFUNCS, PADFLPCB, PASFLPCB, PAUTBPCB | CBLTDLI |
| `app/app-authorization-ims-db2-mq/cbl/PAUDBLOD.CBL` | 251 | subroutine+ims | CIPAUDTY, CIPAUSMY, IMSFUNCS, PAUTBPCB | CBLTDLI |
| `app/app-authorization-ims-db2-mq/cbl/PAUDBUNL.CBL` | 207 | subroutine+ims | CIPAUDTY, CIPAUSMY, IMSFUNCS, PAUTBPCB | CBLTDLI |
| `app/app-authorization-ims-db2-mq/cpy-bms/COPAU00.cpy` | 764 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy-bms/COPAU01.cpy` | 344 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/CCPAUERY.cpy` | 40 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/CCPAURLY.cpy` | 24 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/CCPAURQY.cpy` | 36 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/CIPAUDTY.cpy` | 54 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/CIPAUSMY.cpy` | 31 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/IMSFUNCS.cpy` | 27 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/PADFLPCB.CPY` | 26 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/PASFLPCB.CPY` | 26 | — | — | — |
| `app/app-authorization-ims-db2-mq/cpy/PAUTBPCB.CPY` | 26 | — | — | — |
| `app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd` | 79 | — | — | — |
| `app/app-authorization-ims-db2-mq/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat` | 11 | — | — | — |
| `app/app-authorization-ims-db2-mq/dcl/AUTHFRDS.dcl` | 89 | — | — | — |
| `app/app-authorization-ims-db2-mq/ddl/AUTHFRDS.ddl` | 28 | — | — | — |
| `app/app-authorization-ims-db2-mq/ddl/XAUTHFRD.ddl` | 4 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/DBPAUTP0.dbd` | 41 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/DBPAUTX0.dbd` | 34 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/DLIGSAMP.PSB` | 24 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/PADFLDBD.DBD` | 30 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/PASFLDBD.DBD` | 30 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/PAUTBUNL.PSB` | 22 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/PSBPAUTB.psb` | 21 | — | — | — |
| `app/app-authorization-ims-db2-mq/ims/PSBPAUTL.psb` | 21 | — | — | — |
| `app/app-authorization-ims-db2-mq/jcl/CBPAUP0J.jcl` | 47 | — | — | — |
| `app/app-authorization-ims-db2-mq/jcl/DBPAUTP0.jcl` | 47 | — | — | — |
| `app/app-authorization-ims-db2-mq/jcl/LOADPADB.JCL` | 51 | — | — | — |
| `app/app-authorization-ims-db2-mq/jcl/UNLDGSAM.JCL` | 53 | — | — | — |
| `app/app-authorization-ims-db2-mq/jcl/UNLDPADB.JCL` | 69 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
`app/app-authorization-ims-db2-mq`: IMS HIDAM → `pending_auth_summary`/`pending_auth_detail` (FK + indice sulla chiave inversa del timestamp); DL/I → repository `findFirst/next/insert/replace/delete`; DB2 AUTHFRDS → entity JPA; CP00 (trigger MQ) → listener SQS request/reply (`CCPAURQY`/`CCPAURLY`) via `MessageChannelPort`; CPVS/CPVD → REST; CBPAUP0J (purge + ricalcolo credito) → job Spring Batch; unload/load IMS → job dati con `IMSDATA.DBPAUTP0.dat` come golden. Le tabelle nuove vanno nel tuo range Flyway, entity/repository nel tuo modulo. Attivabile con `carddemo.extensions.authorization.enabled=true` (default off).

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `Copau00Screen`, `Copau01Screen`
- Porte (`com.carddemo.contracts.port`): `MessageChannelPort`, `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `account`, `card_xref`, `(nuove) pending_auth_summary, pending_auth_detail, auth_fraud`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/screens/copau00` — CPVS COPAUS0C - first send of map COPAU0A _(opzionale, estensione)_
  - `POST /api/v1/screens/copau00` — CPVS COPAUS0C - receive map COPAU0A (AID key + fields) _(opzionale, estensione)_
  - `GET /api/v1/screens/copau01` — CPVD COPAUS1C - first send of map COPAU1A _(opzionale, estensione)_
  - `POST /api/v1/screens/copau01` — CPVD COPAUS1C - receive map COPAU1A (AID key + fields) _(opzionale, estensione)_
- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).
- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.
- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.

### Protocollo CONTRACT-CHANGE (§4.4)
Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.

## Definition of Done
- [ ] Parità delle decisioni approva/rifiuta su un set di richieste golden; purge identico; marcatura frode → riga AUTHFRDS.
- [ ] Test di integrazione LocalStack SQS (profilo `it`); regressione app base con flag ON e OFF.
- [ ] Copertura ≥ 80%; ArchUnit verde.
- [ ] Golden specifici: nuovi online/CPVS, online/CPVD, batch CBPAUP0J, `IMSDATA.DBPAUTP0.dat`.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
