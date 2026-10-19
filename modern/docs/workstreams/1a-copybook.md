# 1a-copybook — Copybook → record Java (codec completo)

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/1a-copybook` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 1 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-copybook/**`
- `modern/carddemo-parity/src/test/resources/golden/1a-copybook/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/1a-copybook/**` (golden batch nuovi: `.../golden/1a-copybook/<JOB>/`; scenari online: `.../golden/1a-copybook/online/<TRANID>/*.yaml`)
- Flyway: nessuna migrazione (non creare file Flyway)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
22 file, 693 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/cpy/COCOM01Y.cpy` | 47 | — | — | — |
| `app/cpy/COTTL01Y.cpy` | 27 | — | — | — |
| `app/cpy/CSMSG01Y.cpy` | 24 | — | — | — |
| `app/cpy/CSMSG02Y.cpy` | 35 | — | — | — |
| `app/cpy/CSSETATY.cpy` | 30 | — | — | — |
| `app/cpy/CSSTRPFY.cpy` | 85 | — | — | — |
| `app/cpy/CSUSR01Y.cpy` | 26 | — | — | — |
| `app/cpy/CUSTREC.cpy` | 26 | — | — | — |
| `app/cpy/CVACT01Y.cpy` | 20 | — | — | — |
| `app/cpy/CVACT02Y.cpy` | 14 | — | — | — |
| `app/cpy/CVACT03Y.cpy` | 11 | — | — | — |
| `app/cpy/CVCRD01Y.cpy` | 46 | — | — | — |
| `app/cpy/CVCUS01Y.cpy` | 26 | — | — | — |
| `app/cpy/CVEXPORT.cpy` | 103 | — | — | — |
| `app/cpy/CVTRA01Y.cpy` | 13 | — | — | — |
| `app/cpy/CVTRA02Y.cpy` | 13 | — | — | — |
| `app/cpy/CVTRA03Y.cpy` | 10 | — | — | — |
| `app/cpy/CVTRA04Y.cpy` | 12 | — | — | — |
| `app/cpy/CVTRA05Y.cpy` | 21 | — | — | — |
| `app/cpy/CVTRA06Y.cpy` | 21 | — | — | — |
| `app/cpy/CVTRA07Y.cpy` | 73 | — | — | — |
| `app/cpy/UNUSED1Y.cpy` | 10 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
Parser copybook → AST (JRecord/cb2xml o codegen dedicato, decidi e aggiorna l'ADR-004 nella PR come proposta) e generatore deterministico che produce un `record` Java + `RecordLayout` (offset/lunghezza/PIC) per ogni 01-level. Supporto DISPLAY zoned + overpunch, `COMP`/`BINARY` big-endian 2/4/8, `COMP-3`, `OCCURS`/`OCCURS DEPENDING ON`, `REDEFINES` → `sealed interface` (es. `ExportRecord permits CustomerExport, AccountExport, ...` discriminato da `EXPORT-REC-TYPE`), 88-level → enum/boolean, record VB (RDW), EBCDIC CP037 ↔ UTF-8. Sostituisci gli interni di `ZonedRecordCodec`/`CoreCodecs` senza cambiarne l'API pubblica.

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: tutti i record di `com.carddemo.contracts.record` (annotazioni `@CobolRecord`/`@CobolField`)
- Porte (`com.carddemo.contracts.port`): `RecordCodec<T>`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): —
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): —
- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).
- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.
- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.

### Protocollo CONTRACT-CHANGE (§4.4)
Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.

## Definition of Done
- [ ] Round-trip byte-identico (decode → encode) su ogni record di `app/data/ASCII` e `app/data/EBCDIC` (incluso `EXPORT.DATA.PS` e `IMSDATA.DBPAUTP0.dat`).
- [ ] Property test su COMP-3/COMP con segno e limiti; codegen deterministico (rigenerazione = zero diff).
- [ ] Record generati per CVEXPORT, CSUTLDWY/PY, formati `ACCTDATA.PSCOMP`/`ARRYPS`/`VBPS` di CBACT01C e copybook IMS/DB2 delle estensioni.
- [ ] `GoldenMasterIntegrityTest` e `CoreCodecsRoundTripTest` restano verdi.
- [ ] Golden specifici: tutti i golden batch esistenti (`GoldenMasterIntegrityTest` li decodifica con i tuoi codec).
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
