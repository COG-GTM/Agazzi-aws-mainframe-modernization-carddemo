# o5-useradmin — Amministrazione utenti

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/o5-useradmin` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 3 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-online-useradmin/**`
- `modern/carddemo-parity/src/test/resources/golden/o5-useradmin/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/o5-useradmin/**` (golden batch nuovi: `.../golden/o5-useradmin/<JOB>/`; scenari online: `.../golden/o5-useradmin/online/<TRANID>/*.yaml`)
- Flyway: `V1400–V1499` (file in `modern/carddemo-online-useradmin/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
12 file, 3924 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/bms/COUSR00.bms` | 463 | — | — | — |
| `app/bms/COUSR01.bms` | 164 | — | — | — |
| `app/bms/COUSR02.bms` | 169 | — | — | — |
| `app/bms/COUSR03.bms` | 153 | — | — | — |
| `app/cbl/COUSR00C.cbl` | 531 | online | COCOM01Y, COTTL01Y, COUSR00, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/cbl/COUSR01C.cbl` | 198 | online | COCOM01Y, COTTL01Y, COUSR01, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/cbl/COUSR02C.cbl` | 303 | online | COCOM01Y, COTTL01Y, COUSR02, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/cbl/COUSR03C.cbl` | 251 | online | COCOM01Y, COTTL01Y, COUSR03, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/cpy-bms/COUSR00.CPY` | 728 | — | — | — |
| `app/cpy-bms/COUSR01.CPY` | 164 | — | — | — |
| `app/cpy-bms/COUSR02.CPY` | 164 | — | — | — |
| `app/cpy-bms/COUSR03.CPY` | 152 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
CU00–CU03 (COUSR00C–03C): CRUD `app_user` con paginazione; solo ruolo ADMIN; password BCrypt, mai restituita dalle API.

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `Cousr00Screen`, `Cousr01Screen`, `Cousr02Screen`, `Cousr03Screen`, `UserSecurityRecord`
- Porte (`com.carddemo.contracts.port`): `UserCredentialService`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `app_user`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/screens/cousr00` — CU00 COUSR00C - first send of map COUSR0A
  - `POST /api/v1/screens/cousr00` — CU00 COUSR00C - receive map COUSR0A (AID key + fields)
  - `GET /api/v1/screens/cousr01` — CU01 COUSR01C - first send of map COUSR1A
  - `POST /api/v1/screens/cousr01` — CU01 COUSR01C - receive map COUSR1A (AID key + fields)
  - `GET /api/v1/screens/cousr02` — CU02 COUSR02C - first send of map COUSR2A
  - `POST /api/v1/screens/cousr02` — CU02 COUSR02C - receive map COUSR2A (AID key + fields)
  - `GET /api/v1/screens/cousr03` — CU03 COUSR03C - first send of map COUSR3A
  - `POST /api/v1/screens/cousr03` — CU03 COUSR03C - receive map COUSR3A (AID key + fields)
- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).
- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.
- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.

### Protocollo CONTRACT-CHANGE (§4.4)
Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.

## Definition of Done
- [ ] Una risorsa REST per mappa BMS (`GET` primo invio, `POST /api/v1/screens/<mappa>` con `action` + campi) conforme a `openapi.yaml` (test di contratto); risposta sempre `ScreenResponse<T>`.
- [ ] Logica dei paragrafi COBOL in `@Service` con le stesse validazioni, nello stesso ordine e con gli stessi testi dei messaggi; `ConversationContext` in sessione.
- [ ] Scenari YAML golden verdi: happy path, ogni messaggio di errore del programma COBOL, navigazione PF3/PF7/PF8/ENTER.
- [ ] Test di autorizzazione (utente vs admin, dati di altri utenti) con lo stesso comportamento del COBOL; correzioni di sicurezza registrate in `PARITY_EXCEPTIONS.md` come `proposed` (sezione PARITY-EXCEPTION della PR).
- [ ] Concorrenza: test di aggiornamento simultaneo (optimistic lock `@Version`) dove il programma fa REWRITE.
- [ ] Copertura JaCoCo ≥ 80% sul modulo; ArchUnit verde.
- [ ] Golden specifici: nuovi online/CU00, CU01, CU02, CU03.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
