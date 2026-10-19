# o4-transaction — Transazioni, pagamenti, report

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/o4-transaction` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 3 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-online-transaction/**`
- `modern/carddemo-parity/src/test/resources/golden/o4-transaction/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/o4-transaction/**` (golden batch nuovi: `.../golden/o4-transaction/<JOB>/`; scenari online: `.../golden/o4-transaction/online/<TRANID>/*.yaml`)
- Flyway: `V1300–V1399` (file in `modern/carddemo-online-transaction/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
15 file, 6085 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/bms/COBIL00.bms` | 141 | — | — | — |
| `app/bms/CORPT00.bms` | 231 | — | — | — |
| `app/bms/COTRN00.bms` | 464 | — | — | — |
| `app/bms/COTRN01.bms` | 273 | — | — | — |
| `app/bms/COTRN02.bms` | 307 | — | — | — |
| `app/cbl/COBIL00C.cbl` | 420 | online | COBIL00, COCOM01Y, COTTL01Y, CSDAT01Y, CSMSG01Y, CVACT01Y, CVACT03Y, CVTRA05Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/cbl/CORPT00C.cbl` | 498 | online | COCOM01Y, CORPT00, COTTL01Y, CSDAT01Y, CSMSG01Y, CVTRA05Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, CSUTLDTC |
| `app/cbl/COTRN00C.cbl` | 529 | online | COCOM01Y, COTRN00, COTTL01Y, CSDAT01Y, CSMSG01Y, CVTRA05Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/cbl/COTRN01C.cbl` | 231 | online | COCOM01Y, COTRN01, COTTL01Y, CSDAT01Y, CSMSG01Y, CVTRA05Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/cbl/COTRN02C.cbl` | 614 | online | COCOM01Y, COTRN02, COTTL01Y, CSDAT01Y, CSMSG01Y, CVACT01Y, CVACT03Y, CVTRA05Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, CSUTLDTC |
| `app/cpy-bms/COBIL00.CPY` | 140 | — | — | — |
| `app/cpy-bms/CORPT00.CPY` | 224 | — | — | — |
| `app/cpy-bms/COTRN00.CPY` | 728 | — | — | — |
| `app/cpy-bms/COTRN01.CPY` | 272 | — | — | — |
| `app/cpy-bms/COTRN02.CPY` | 272 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
CT00, CT01, CT02, CB00, CR00 (COTRN00C–02C, COBIL00C, CORPT00C). ID transazione (ultimo ID + 1) sotto concorrenza → sequence o lock pessimistico con la stessa semantica; CB00 azzera il saldo e crea la transazione di pagamento; CR00 chiama `ReportJobLauncherPort` (implementata da B3; in Fase 0 c'è `UnavailableReportJobLauncher`) al posto della TDQ; stato job via `GET /api/v1/jobs/{executionId}`.

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `Cotrn00Screen`, `Cotrn01Screen`, `Cotrn02Screen`, `Cobil00Screen`, `Corpt00Screen`, `TransactionRecord`
- Porte (`com.carddemo.contracts.port`): `ReportJobLauncherPort`, `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `transaction`, `account`, `card_xref`, `transaction_type`, `transaction_category`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/screens/cobil00` — CB00 COBIL00C - first send of map COBIL0A
  - `POST /api/v1/screens/cobil00` — CB00 COBIL00C - receive map COBIL0A (AID key + fields)
  - `GET /api/v1/screens/corpt00` — CR00 CORPT00C - first send of map CORPT0A
  - `POST /api/v1/screens/corpt00` — CR00 CORPT00C - receive map CORPT0A (AID key + fields)
  - `GET /api/v1/screens/cotrn00` — CT00 COTRN00C - first send of map COTRN0A
  - `POST /api/v1/screens/cotrn00` — CT00 COTRN00C - receive map COTRN0A (AID key + fields)
  - `GET /api/v1/screens/cotrn01` — CT01 COTRN01C - first send of map COTRN1A
  - `POST /api/v1/screens/cotrn01` — CT01 COTRN01C - receive map COTRN1A (AID key + fields)
  - `GET /api/v1/screens/cotrn02` — CT02 COTRN02C - first send of map COTRN2A
  - `POST /api/v1/screens/cotrn02` — CT02 COTRN02C - receive map COTRN2A (AID key + fields)
  - `GET /api/v1/jobs/{executionId}` — Status of a report job submitted by CR00 (ReportJobLauncherPort, implemented by B3/B5)
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
- [ ] Golden specifici: nuovi online/CT00, CT01, CT02, CB00, CR00.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
