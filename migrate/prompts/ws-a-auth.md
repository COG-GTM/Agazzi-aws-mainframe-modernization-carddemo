# Fase 1 - ws-a-auth: autenticazione e gestione utenti -> Spring Security

Package dedicato: `com.carddemo.auth` (modulo `modern/carddemo-auth`).

## Sorgenti legacy da migrare
| Programma | TransID | Mappa BMS | Funzione |
|---|---|---|---|
| `app/cbl/COSGN00C.cbl` | CC00 | `COSGN00` | Sign-on: legge `USRSEC`, verifica password, instrada a menu admin (`COADM01C`) o utente (`COMEN01C`) |
| `app/cbl/COUSR00C.cbl` | CU00 | `COUSR00` | Lista utenti paginata (browse `USRSEC`) |
| `app/cbl/COUSR01C.cbl` | CU01 | `COUSR01` | Aggiunta utente Regular/Admin |
| `app/cbl/COUSR02C.cbl` | CU02 | `COUSR02` | Modifica utente |
| `app/cbl/COUSR03C.cbl` | CU03 | `COUSR03` | Cancellazione utente |
| `app/cbl/COADM01C.cbl` | CA00 | `COADM01` | Menu amministratore (opzioni in `app/cpy/COADM02Y.cpy`) |

Dati: file VSAM `USRSEC` (copybook `app/cpy/CSUSR01Y.cpy`, cluster in `app/jcl/DUSRSECJ.jcl`) -> entity `SecUser` / tabella `sec_user` di `carddemo-common`. COMMAREA: `app/cpy/COCOM01Y.cpy`.

## Deliverable
- Spring Security 6 stateless: `POST /api/auth/login` (user id + password, upper-case come in COSGN00C) -> JWT (claims: `sub`, `role`, `firstName`, `lastName`), `POST /api/auth/logout`, `GET /api/auth/me`. `UserDetailsService` su `SecUserRepository`.
- Ruoli: `SEC-USR-TYPE = 'A'` -> `ROLE_ADMIN`, `'U'` -> `ROLE_USER`. Espone in `com.carddemo.auth.security` la configurazione condivisa (`SecurityFilterChain`, JWT filter, `@EnableMethodSecurity`) che gli altri moduli usano solo tramite `@PreAuthorize`.
- Migrazione Flyway `V100__hash_user_passwords.sql`: converte le password `{noop}` seedate dalla fase 0 in BCrypt (`pgcrypto crypt(..., gen_salt('bf'))`, compatibile con `BCryptPasswordEncoder`). `DelegatingPasswordEncoder`.
- CRUD utenti (solo `ADMIN`): `GET /api/admin/users?startAfter=&size=10` (keyset come STARTBR/READNEXT di COUSR00C, anche pagina precedente), `POST /api/admin/users`, `PUT /api/admin/users/{id}`, `DELETE /api/admin/users/{id}`; stesse validazioni e messaggi di COUSR01C-03C (campi obbligatori, id duplicato -> 409, utente non trovato -> 404).
- `GET /api/menu`: opzioni di menu per ruolo, derivate da `COADM02Y` (admin) e `COMEN02Y` (utente), cosi' la UI (ws-i) non le cabla.
- Rate limiting/lockout opzionale documentato, password mai loggate.

## Criteri di accettazione
- Login con `ADMIN001`/`PASSWORDA` -> ruolo ADMIN; `USER0001`/`PASSWORDU` -> USER; password errata -> 401 con messaggio equivalente a COSGN00C.
- Un USER riceve 403 su `/api/admin/**`.
- Test Testcontainers per login, JWT, CRUD utenti, paginazione avanti/indietro.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.auth` nel modulo `modern/carddemo-auth`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-auth/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V100-V199`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-auth -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-a-auth`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-a-auth] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
