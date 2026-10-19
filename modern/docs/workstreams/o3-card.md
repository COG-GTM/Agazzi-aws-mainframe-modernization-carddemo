# o3-card — Carte: lista, dettaglio, aggiornamento

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/o3-card` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 3 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-online-card/**`
- `modern/carddemo-parity/src/test/resources/golden/o3-card/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/o3-card/**` (golden batch nuovi: `.../golden/o3-card/<JOB>/`; scenari online: `.../golden/o3-card/online/<TRANID>/*.yaml`)
- Flyway: `V1200–V1299` (file in `modern/carddemo-online-card/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
9 file, 5563 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/bms/COCRDLI.bms` | 344 | — | — | — |
| `app/bms/COCRDSL.bms` | 157 | — | — | — |
| `app/bms/COCRDUP.bms` | 172 | — | — | — |
| `app/cbl/COCRDLIC.cbl` | 1093 | online | COCOM01Y, COCRDLI, COTTL01Y, CSDAT01Y, CSMSG01Y, CSSTRPFY, CSUSR01Y, CVACT02Y, CVCRD01Y, DFHAID, DFHBMSCA | CCARD-NEXT-PROG, COCRDSLC, COCRDUPC, COMEN01C |
| `app/cbl/COCRDSLC.cbl` | 642 | online | COCOM01Y, COCRDSL, COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CSSTRPFY, CSUSR01Y, CVACT02Y, CVCRD01Y, CVCUS01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, COCRDLIC, COMEN01C |
| `app/cbl/COCRDUPC.cbl` | 1194 | online | COCOM01Y, COCRDUP, COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CSSTRPFY, CSUSR01Y, CVACT02Y, CVCRD01Y, CVCUS01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, COCRDLIC, COCRDSLC, COMEN01C |
| `app/cpy-bms/COCRDLI.CPY` | 560 | — | — | — |
| `app/cpy-bms/COCRDSL.CPY` | 200 | — | — | — |
| `app/cpy-bms/COCRDUP.CPY` | 224 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
CCLI, CCDL, CCUP (COCRDLIC, COCRDSLC, COCRDUPC): paginazione PF7/PF8 a 7 righe con filtri conto/carta; selezione `S`/`U` per riga; l'utente non admin vede solo le proprie carte (logica esistente).

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `CocrdliScreen`, `CocrdslScreen`, `CocrdupScreen`, `CardRecord`
- Porte (`com.carddemo.contracts.port`): `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `card`, `card_xref`, `account`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/screens/cocrdli` — CCLI COCRDLIC - first send of map CCRDLIA
  - `POST /api/v1/screens/cocrdli` — CCLI COCRDLIC - receive map CCRDLIA (AID key + fields)
  - `GET /api/v1/screens/cocrdsl` — CCDL COCRDSLC - first send of map CCRDSLA
  - `POST /api/v1/screens/cocrdsl` — CCDL COCRDSLC - receive map CCRDSLA (AID key + fields)
  - `GET /api/v1/screens/cocrdup` — CCUP COCRDUPC - first send of map CCRDUPA
  - `POST /api/v1/screens/cocrdup` — CCUP COCRDUPC - receive map CCRDUPA (AID key + fields)
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
- [ ] Golden specifici: nuovi online/CCLI, online/CCDL, online/CCUP.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
