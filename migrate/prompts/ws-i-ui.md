# Fase 2 - ws-i-ui: mappe BMS -> frontend React

Package dedicato: `com.carddemo.ui` (modulo `modern/carddemo-ui`, che impacchetta e serve la SPA) + sorgenti frontend in `modern/frontend/`.

Le API REST della fase 1 sono gia' mergiate nel branch base: leggi l'OpenAPI (`/v3/api-docs` di `carddemo-app`) e genera il client TypeScript, non cablare URL a mano.

## Sorgenti legacy da migrare
Ogni mappa BMS di `app/bms/` (layout campi in `app/cpy-bms/*.CPY`) diventa una pagina React:

| Mappa | Programma / TransID | Pagina | API |
|---|---|---|---|
| `COSGN00` | COSGN00C / CC00 | `/login` | ws-a `/api/auth` |
| `COMEN01` | COMEN01C / CM00 | `/menu` (utente) | ws-a `/api/menu` |
| `COADM01` | COADM01C / CA00 | `/admin` (menu admin) | ws-a `/api/menu` |
| `COUSR00`-`COUSR03` | COUSR00C-03C / CU00-CU03 | `/admin/users`, `/admin/users/new`, `/admin/users/:id/edit`, `/admin/users/:id/delete` | ws-a `/api/admin/users` |
| `COACTVW` / `COACTUP` | COACTVWC / COACTUPC | `/accounts/:id`, `/accounts/:id/edit` | ws-b `/api/accounts` |
| `COCRDLI` / `COCRDSL` / `COCRDUP` | COCRDLIC / COCRDSLC / COCRDUPC | `/cards`, `/cards/:num`, `/cards/:num/edit` | ws-c `/api/cards` |
| `COTRN00` / `COTRN01` / `COTRN02` | COTRN00C-02C | `/transactions`, `/transactions/:id`, `/transactions/new` | ws-e `/api/transactions` |
| `COBIL00` | COBIL00C / CB00 | `/accounts/:id/bill-payment` | ws-e |
| `CORPT00` | CORPT00C / CR00 | `/reports` | ws-h `/api/reports` |

## Deliverable
- `modern/frontend/`: React 18 + TypeScript + Vite, React Router, TanStack Query, client generato da OpenAPI (`openapi-typescript` o `orval`), form con validazione (react-hook-form + zod) che rispecchia le regole dei programmi COBOL e mostra i messaggi `ProblemDetail` del backend nella riga messaggi (come `ERRMSG` delle mappe).
- Fedelta' funzionale alle mappe: stessi campi, stesse etichette, stessi tasti funzione resi come pulsanti/scorciatoie da tastiera (`F3` indietro/esci, `F7`/`F8` pagina precedente/successiva, `Enter` invio, `F4` pulisci, `F5` salva dove previsto), intestazione con titolo/transazione/data/ora (`COTTL01Y`, `CSDAT01Y`). Layout moderno e responsive, non un emulatore 3270.
- Autenticazione: login JWT (ws-a), route protette per ruolo (ADMIN/USER), logout, gestione 401/403.
- `carddemo-ui`: build del frontend nel ciclo Maven (`frontend-maven-plugin`), output in `META-INF/resources`, `com.carddemo.ui.SpaForwardingController` per il fallback delle route client.
- Test: Vitest + Testing Library per i componenti/form; Playwright per i flussi end-to-end principali (login admin e utente, lista/dettaglio/update carta, nuova transazione, report) contro backend avviato con Testcontainers/docker-compose; test Java del controller SPA.

## Criteri di accettazione
- Tutte le 17 mappe di `app/bms/` hanno una pagina corrispondente (tabella di copertura nella PR con screenshot).
- `npm run lint`, `npm test`, `npx playwright test` e `mvn -pl carddemo-ui -am verify` verdi.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.ui` nel modulo `modern/carddemo-ui`. Non creare ne' modificare classi in package/moduli di altri workstream.
- Entity e repository condivisi stanno in `modern/carddemo-common` (`com.carddemo.common.entity`, `com.carddemo.common.repository`), creati dalla fase 0: riusali, non duplicarli. Se serve una modifica in `carddemo-common`, deve essere solo additiva (nuovo metodo di repository, nuovo campo nullable) e va elencata nella PR sotto "Modifiche condivise".
- Non modificare i sorgenti legacy in `app/` (COBOL, JCL, copybook, BMS, dati): sono la specifica di riferimento.
- Tracciabilita': ogni classe/metodo che porta logica COBOL ha un Javadoc con programma e paragrafo di origine, es. `/** COBOL: CBTRN02C 1500-VALIDATE-TRAN */`.

### Copybook -> JPA
- Un record `01` = una `@Entity`; tabella e colonne in snake_case del nome COBOL (es. `ACCT-CURR-BAL` -> colonna `acct_curr_bal`, campo `acctCurrBal`). Javadoc del campo con nome COBOL e PIC originale.
- `COMP-3` (packed decimal) e numerici con decimali impliciti `PIC S9(n)V9(m)` (anche DISPLAY/zoned) -> `java.math.BigDecimal` con `@Column(precision = n+m, scale = m)`. Mai `double`/`float`. Aritmetica: COBOL tronca, quindi `RoundingMode.DOWN` salvo dove il sorgente usa `ROUNDED` (`RoundingMode.HALF_UP`).
- `PIC 9(n)` usati come ID/chiave -> `Long`; codici con zeri significativi (es. `TRAN-CAT-CD PIC 9(04)` in chiave composta) -> mantieni il tipo scelto dalla fase 0 in `carddemo-common`.
- `PIC X(n)` -> `String` con `@Column(length = n)`; gli spazi di riempimento a destra vengono rimossi in lettura (converter della fase 0).
- Date `X(10)` `YYYY-MM-DD` -> `LocalDate`; timestamp `X(26)` `YYYY-MM-DD-HH.MM.SS.ffffff` -> `LocalDateTime`.
- `COMP`/`COMP-4`/`BINARY` -> `int`/`long`. `FILLER` ignorato. `REDEFINES` -> DTO/varianti distinte. `OCCURS` -> colonne numerate o `@ElementCollection`. Chiavi di gruppo (es. `TRAN-CAT-KEY`) -> `@EmbeddedId`.
- Nei file dati di `app/data/ASCII` i numerici con segno sono zoned con overpunch (`{`=+0, `A`-`I`=+1..+9, `}`=-0, `J`-`R`=-1..-9): usa il codec `com.carddemo.common.codec` della fase 0, non riscriverlo.

### EXEC CICS -> REST
- Ogni transazione/programma CICS diventa un `@RestController` sotto `/api/<dominio>/...`; la logica sta in un `@Service` transazionale, mai nel controller.
- `SEND MAP`/`RECEIVE MAP` -> DTO `record` di request/response con i campi della mappa (vedi `app/cpy-bms/*.CPY`), validati con Bean Validation; i messaggi di errore riprendono il testo COBOL (`WS-MESSAGE`/`ERRMSG`).
- `READ`/`WRITE`/`REWRITE`/`DELETE FILE` -> chiamate a repository dentro `@Transactional`; `READ ... UPDATE` + `REWRITE` -> optimistic locking (`@Version`) o `PESSIMISTIC_WRITE` dove il COBOL confronta i valori prima dell'update.
- `STARTBR`/`READNEXT`/`READPREV`/`ENDBR` -> paginazione keyset (`Slice`/`Window`) con la stessa dimensione pagina della mappa (es. 10 righe per le liste).
- `XCTL`/`LINK` -> chiamata a service o navigazione lato UI; `COMMAREA` (`COCOM01Y`) -> nessuno stato di sessione server: identita' dal token Spring Security, contesto via parametri di request.
- Codici `RESP`: `NOTFND` -> 404, `DUPREC`/`DUPKEY` -> 409, errori di validazione -> 400, `ABEND`/altri -> 500, sempre come `ProblemDetail` (RFC 7807).
- `SYNCPOINT`/`SYNCPOINT ROLLBACK` -> confini di transazione Spring. Autorizzazione con `@PreAuthorize` sui ruoli `ADMIN`/`USER` definiti da ws-a-auth (`SEC-USR-TYPE` `A`/`U`).
- Documenta le API con springdoc-openapi (`/v3/api-docs`).

### VSAM -> Spring Data JPA
- KSDS -> tabella con primary key = `KEYS(len offset)` del `DEFINE CLUSTER` nei JCL di `app/jcl/`.
- AIX/PATH -> indice secondario su PostgreSQL + query method derivato (es. `findByXrefAcctId`).
- ESDS -> tabella con chiave surrogata sequenziale; RRDS -> colonna numero di record relativo.
- File sequenziali (PS) e GDG `(+1)`/`(0)` -> file in `data/in` / `data/out` letti/scritti con `FlatFileItemReader`/`FlatFileItemWriter`, generazione GDG = suffisso `.G<nnnn>` o istanza di job Spring Batch.
- Repository Spring Data `JpaRepository`; niente SQL nativo se non per performance documentata.

### Flyway
- Le migrazioni di questo workstream vanno in `modern/carddemo-ui/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `nessuna migrazione prevista (V900-V999 se indispensabile)`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-ui -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-i-ui`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-i-ui] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
