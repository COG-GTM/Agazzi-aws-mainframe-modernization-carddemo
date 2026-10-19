# o2-account — Conto: visualizzazione e aggiornamento

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/o2-account` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 3 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-online-account/**`
- `modern/carddemo-parity/src/test/resources/golden/o2-account/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/o2-account/**` (golden batch nuovi: `.../golden/o2-account/<JOB>/`; scenari online: `.../golden/o2-account/online/<TRANID>/*.yaml`)
- Flyway: `V1100–V1199` (file in `modern/carddemo-online-account/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
7 file, 8517 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/bms/COACTUP.bms` | 512 | — | — | — |
| `app/bms/COACTVW.bms` | 378 | — | — | — |
| `app/cbl/COACTUPC.cbl` | 3368 | online | COACTUP, COCOM01Y, COTTL01Y, CSDAT01Y, CSLKPCDY, CSMSG01Y, CSMSG02Y, CSSETATY, CSSTRPFY, CSUSR01Y, CSUTLDPY, CSUTLDWY, CVACT01Y, CVACT03Y, CVCRD01Y, CVCUS01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C |
| `app/cbl/COACTVWC.cbl` | 703 | online | COACTVW, COCOM01Y, COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CSSTRPFY, CSUSR01Y, CVACT01Y, CVACT02Y, CVACT03Y, CVCRD01Y, CVCUS01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, COCRDLIC, COCRDSLC, COCRDUPC, COMEN01C |
| `app/cpy-bms/COACTUP.CPY` | 668 | — | — | — |
| `app/cpy-bms/COACTVW.CPY` | 464 | — | — | — |
| `app/cpy/CSLKPCDY.cpy` | 1318 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
CAVW, CAUP (COACTVWC, COACTUPC — il programma più grande, 4.236 righe): validazioni campo per campo (date, SSN, telefono, stato, ZIP via `CSLKPCDY`), confronto old vs new prima del REWRITE → `@Version` + diff; aggiornamento atomico account + customer.

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `CoactvwScreen`, `CoactupScreen`, `AccountRecord`, `CustomerRecord`
- Porte (`com.carddemo.contracts.port`): `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `account`, `customer`, `card_xref`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/screens/coactup` — CAUP COACTUPC - first send of map CACTUPA
  - `POST /api/v1/screens/coactup` — CAUP COACTUPC - receive map CACTUPA (AID key + fields)
  - `GET /api/v1/screens/coactvw` — CAVW COACTVWC - first send of map CACTVWA
  - `POST /api/v1/screens/coactvw` — CAVW COACTVWC - receive map CACTVWA (AID key + fields)
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
- [ ] Golden specifici: nuovi online/CAVW, online/CAUP.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
