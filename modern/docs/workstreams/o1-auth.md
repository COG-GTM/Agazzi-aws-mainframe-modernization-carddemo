# o1-auth — Sign-on, menu, sicurezza

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/o1-auth` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 3 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-online-auth/**`
- `modern/carddemo-parity/src/test/resources/golden/o1-auth/**`
- `modern/carddemo-parity/src/test/resources/golden/online/CC00/**`
- `modern/carddemo-parity/src/test/resources/golden/online/CM00/**`
- `modern/carddemo-parity/src/test/resources/golden/online/CA00/**`
- `modern/carddemo-parity/src/test/java/com/carddemo/parity/online/CC00SignOnScenariosIT.java`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/o1-auth/**` (golden batch nuovi: `.../golden/o1-auth/<JOB>/`; scenari online: `.../golden/o1-auth/online/<TRANID>/*.yaml`)
- Flyway: `V1000–V1099` (file in `modern/carddemo-online-auth/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
12 file, 2740 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/bms/COADM01.bms` | 167 | — | — | — |
| `app/bms/COMEN01.bms` | 167 | — | — | — |
| `app/bms/COSGN00.bms` | 210 | — | — | — |
| `app/cbl/COADM01C.cbl` | 189 | online | COADM01, COADM02Y, COCOM01Y, COTTL01Y, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA | CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION, CDEMO-TO-PROGRAM |
| `app/cbl/COMEN01C.cbl` | 213 | online | COCOM01Y, COMEN01, COMEN02Y, COTTL01Y, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA | CDEMO-MENU-OPT-PGMNAME(WS-OPTION, CDEMO-TO-PROGRAM |
| `app/cbl/COSGN00C.cbl` | 172 | online | COCOM01Y, COSGN00, COTTL01Y, CSDAT01Y, CSMSG01Y, CSUSR01Y, DFHAID, DFHBMSCA | COADM01C, COMEN01C |
| `app/cpy-bms/COADM01.CPY` | 260 | — | — | — |
| `app/cpy-bms/COMEN01.CPY` | 260 | — | — | — |
| `app/cpy-bms/COSGN00.CPY` | 152 | — | — | — |
| `app/cpy/COADM02Y.cpy` | 62 | — | — | — |
| `app/cpy/COMEN02Y.cpy` | 101 | — | — | — |
| `app/csd/CARDDEMO.CSD` | 505 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
CC00/CM00/CA00 (COSGN00C, COMEN01C, COADM01C). Spring Security su `app_user` via `UserCredentialService` (sostituisce `LegacyUserCredentialService` di Fase 0 e la `SecurityFilterChain` di `Fase0SecurityAutoConfiguration`, entrambe `@ConditionalOnMissingBean`); routing utente/admin; menu da `COMEN02Y`/`COADM02Y` (opzioni delle estensioni visibili solo se `carddemo.extensions.<nome>.enabled=true`); logout = PF3; sessione secondo ADR-002. Rimuovi `@Tag("pending-o1")` da `CC00SignOnScenariosIT` e `status: pending-o1` dagli scenari CC00 quando sono verdi (sono nel tuo perimetro).

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `Cosgn00Screen`, `Comen01Screen`, `Coadm01Screen`, `ConversationContext`, `ScreenResponse<T>`
- Porte (`com.carddemo.contracts.port`): `UserCredentialService`, `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `app_user`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/screens/coadm01` — CA00 COADM01C - first send of map COADM1A
  - `POST /api/v1/screens/coadm01` — CA00 COADM01C - receive map COADM1A (AID key + fields)
  - `GET /api/v1/screens/comen01` — CM00 COMEN01C - first send of map COMEN1A
  - `POST /api/v1/screens/comen01` — CM00 COMEN01C - receive map COMEN1A (AID key + fields)
  - `GET /api/v1/screens/cosgn00` — CC00 COSGN00C - first send of map COSGN0A
  - `POST /api/v1/screens/cosgn00` — CC00 COSGN00C - receive map COSGN0A (AID key + fields)
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
- [ ] Golden specifici: online/CC00 (8 scenari già scritti), nuovi online/CM00, online/CA00.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
