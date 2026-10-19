# x2-trantype — Estensione tipi transazione DB2

Repo: COG-GTM/Agazzi-aws-mainframe-modernization-carddemo · base: `migration/phase-1-4` (contratti tag `contracts-v1`, Fase 0 già mergiata)
Branch: `migration/x2-trantype` · PR verso `migration/phase-1-4` (NON verso `main`, non mergiare: lo fa il leader)
Fase: Fase 4 del piano `docs/MIGRATION_PLAN_JAVA21.md` (leggi §3, §4, §11 e la sezione della tua fase).

## Perimetro (puoi modificare SOLO questi path)
- `modern/carddemo-ext-trantype/**`
- `modern/carddemo-parity/src/test/resources/golden/x2-trantype/**`
- fixture parità: `modern/carddemo-parity/src/test/resources/golden/x2-trantype/**` (golden batch nuovi: `.../golden/x2-trantype/<JOB>/`; scenari online: `.../golden/x2-trantype/online/<TRANID>/*.yaml`)
- Flyway: `V2100–V2199` (file in `modern/carddemo-ext-trantype/src/main/resources/db/migration/`)

Tutto il resto è **read-only**: in particolare NON modificare `modern/pom.xml` (POM padre), `modern/carddemo-contracts/**`, `modern/carddemo-domain/**` esistenti, `V1__baseline.sql`, `V2__seed_sample_data.sql`, `V3__spring_batch_metadata.sql`, né nulla sotto `app/`. Le dipendenze si aggiungono SOLO nel `pom.xml` del tuo modulo (versioni già gestite nel `dependencyManagement` del padre). Qualunque file toccato fuori perimetro è una **violazione di confine** da dichiarare nel messaggio finale.

## Sorgenti legacy da migrare
27 file, 6154 LOC totali (da `modern/ownership.yaml`):

| File | LOC | Tipo | COPY | CALL/XCTL/LINK |
|---|---|---|---|---|
| `app/app-transaction-type-db2/README.md` | 149 | — | — | — |
| `app/app-transaction-type-db2/bms/COTRTLI.bms` | 338 | — | — | — |
| `app/app-transaction-type-db2/bms/COTRTUP.bms` | 137 | — | — | — |
| `app/app-transaction-type-db2/cbl/COBTUPDT.cbl` | 177 | batch+db2 | — | — |
| `app/app-transaction-type-db2/cbl/COTRTLIC.cbl` | 1861 | online+db2 | COCOM01Y, COTRTLI, COTTL01Y, CSDAT01Y, CSMSG01Y, CSSTRPFY, CSUSR01Y, CVACT02Y, CVCRD01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM, COADM01C, COTRTUPC |
| `app/app-transaction-type-db2/cbl/COTRTUPC.cbl` | 1429 | online+db2 | COCOM01Y, COTRTUP, COTTL01Y, CSDAT01Y, CSMSG01Y, CSMSG02Y, CSSETATY, CSSTRPFY, CSUSR01Y, CSUTLDWY, CVCRD01Y, DFHAID, DFHBMSCA | CDEMO-TO-PROGRAM |
| `app/app-transaction-type-db2/cpy-bms/COTRTLI.cpy` | 500 | — | — | — |
| `app/app-transaction-type-db2/cpy-bms/COTRTUP.cpy` | 200 | — | — | — |
| `app/app-transaction-type-db2/cpy/CSDB2RPY.cpy` | 89 | — | — | — |
| `app/app-transaction-type-db2/cpy/CSDB2RWY.cpy` | 46 | — | — | — |
| `app/app-transaction-type-db2/csd/CRDDEMOD.csd` | 60 | — | — | — |
| `app/app-transaction-type-db2/ctl/DB2CREAT.ctl` | 106 | — | — | — |
| `app/app-transaction-type-db2/ctl/DB2FREE.ctl` | 19 | — | — | — |
| `app/app-transaction-type-db2/ctl/DB2LTCAT.ctl` | 39 | — | — | — |
| `app/app-transaction-type-db2/ctl/DB2LTTYP.ctl` | 24 | — | — | — |
| `app/app-transaction-type-db2/ctl/DB2TEP41.ctl` | 18 | — | — | — |
| `app/app-transaction-type-db2/ctl/DB2TIAD1.ctl` | 17 | — | — | — |
| `app/app-transaction-type-db2/ctl/REPROCT.ctl` | 15 | — | — | — |
| `app/app-transaction-type-db2/dcl/DCLTRCAT.dcl` | 54 | — | — | — |
| `app/app-transaction-type-db2/dcl/DCLTRTYP.dcl` | 49 | — | — | — |
| `app/app-transaction-type-db2/ddl/TRNTYCAT.ddl` | 7 | — | — | — |
| `app/app-transaction-type-db2/ddl/TRNTYPE.ddl` | 4 | — | — | — |
| `app/app-transaction-type-db2/ddl/XTRNTYCAT.ddl` | 5 | — | — | — |
| `app/app-transaction-type-db2/ddl/XTRNTYPE.ddl` | 5 | — | — | — |
| `app/app-transaction-type-db2/jcl/CREADB21.jcl` | 84 | — | — | — |
| `app/app-transaction-type-db2/jcl/MNTTRDB2.jcl` | 30 | — | — | — |
| `app/app-transaction-type-db2/jcl/TRANEXTR.jcl` | 122 | — | — | — |

Inventario completo: `modern/docs/inventory/` (`programs.md`, `jcl.md`, `datasets.md`, `bms.md`, `call-graph.mmd`, `inventory.json`).

## Lavoro
`app/app-transaction-type-db2`: CTLI/CTTU → REST su `transaction_type`/`transaction_category` (cursori avanti/indietro → keyset); MNTTRDB2 → job di manutenzione; TRANEXTR sostituito da un controllo di consistenza; opzioni del menu admin CA00 abilitate da `carddemo.extensions.trantype.enabled=true` (default off).

## Contratti da usare (non modificarli: per cambi usa CONTRACT-CHANGE)
- DTO: `CotrtliScreen`, `CotrtupScreen`, `TransactionTypeRecord`, `TransactionCategoryRecord`
- Porte (`com.carddemo.contracts.port`): `BusinessClock`
- Tabelle (V1, entity/repository in `com.carddemo.domain`): `transaction_type`, `transaction_category`
- Endpoint OpenAPI (`carddemo-contracts/src/main/resources/openapi/openapi.yaml`): 
  - `GET /api/v1/screens/cotrtli` — CTLI COTRTLIC - first send of map CTRTLIA _(opzionale, estensione)_
  - `POST /api/v1/screens/cotrtli` — CTLI COTRTLIC - receive map CTRTLIA (AID key + fields) _(opzionale, estensione)_
  - `GET /api/v1/screens/cotrtup` — CTTU COTRTUPC - first send of map CTRTUPA _(opzionale, estensione)_
  - `POST /api/v1/screens/cotrtup` — CTTU COTRTUPC - receive map CTRTUPA (AID key + fields) _(opzionale, estensione)_
- Regole numeriche §3.4: `BigDecimal` con scala del PIC, mai `double`/`float`; `COMPUTE` senza ROUNDED = troncamento (`CobolMath.Mode.TRUNCATE`), con ROUNDED = HALF_UP; `PIC X` padded nel dominio; date solo via `BusinessClock` (ArchUnit vieta `LocalDateTime.now()`).
- Convenzioni: `modern/CONVENTIONS.md`; ADR: `modern/docs/adr/`.
- **Finché 1A/1B/1C non sono mergiati usa le implementazioni minime di Fase 0** (`ZonedRecordCodec`/`CoreCodecs`, seed V2, `FixedBusinessClock`/`SystemBusinessClock`, `CobolMath`, `FileSystemGenerationStore`, `InMemoryMaintenanceMode`, `InMemoryMessageChannel`, fallback in `carddemo-app` con `@ConditionalOnMissingBean`): le interfacce non cambieranno.

### Protocollo CONTRACT-CHANGE (§4.4)
Se ti serve un cambio a `carddemo-contracts`, `carddemo-domain`, V1/V2/V3 o al POM padre **non applicarlo**: aggiungi alla descrizione della PR una sezione `CONTRACT-CHANGE:` con il diff proposto e la motivazione, e lavora con un adattatore locale nel tuo modulo. Sono accettati solo cambi additivi.

## Definition of Done
- [ ] Golden scenari CTLI/CTTU verdi; job MNTTRDB2 con lo stesso file di input/output.
- [ ] Regressione app base con flag ON e OFF.
- [ ] Copertura ≥ 80%; ArchUnit verde.
- [ ] Golden specifici: nuovi online/CTLI, online/CTTU, batch MNTTRDB2.
- [ ] `cd modern && ./mvnw -B verify` e `./mvnw -B -Pparity verify` verdi (gate di parità); `./mvnw -B -Pit verify` verde se hai IT; CI `modern-ci.yml` della PR verde.
- [ ] Nessun file fuori perimetro; `python3 modern/tools/inventory/check_ownership.py` verde.

Golden harness: `modern/tools/golden/run-cobol.sh <JOB> [--verify]` (GnuCOBOL 3.1.2 + faketime; `sudo apt-get install -y gnucobol faketime`), formato in `modern/tools/golden/README.md`; comparatori `RecordComparator` (diff per campo), `DbSnapshot` (delta DB), `OnlineScenarioRunner` (YAML online) in `carddemo-parity`; deviazioni volute solo in `modern/PARITY_EXCEPTIONS.md`.

## Messaggio finale obbligatorio
PR URL, branch, conteggio test (unit / parity / IT), stato gate parità, violazioni di confine, `CONTRACT-CHANGE` richiesti, blocker.
