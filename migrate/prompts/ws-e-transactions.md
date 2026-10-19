# Fase 1 - ws-e-transactions: transazioni online e batch di validazione/report

Package dedicato: `com.carddemo.transaction` (modulo `modern/carddemo-transaction`).

## Sorgenti legacy da migrare
| Programma | TransID / JCL | Funzione |
|---|---|---|
| `app/cbl/COTRN00C.cbl` | CT00, mappa `COTRN00` | Lista transazioni paginata (10 per pagina) da `TRANSACT`, filtro per id transazione di partenza, selezione `S` |
| `app/cbl/COTRN01C.cbl` | CT01, mappa `COTRN01` | Visualizza una transazione |
| `app/cbl/COTRN02C.cbl` | CT02, mappa `COTRN02` | Aggiunge una transazione: lookup carta<->conto via `CXACAIX`/`CCXREF`, validazione campi e date (via `CSUTLDTC`), generazione `TRAN-ID` = ultimo id + 1, conferma `Y/N` |
| `app/cbl/CBTRN01C.cbl` | batch | Legge `DALYTRAN` e verifica per ogni record xref, carta, conto, cliente (senza scrivere): portalo come step di pre-validazione riusabile |
| `app/cbl/CBTRN03C.cbl` | `TRANREPT.jcl` / `app/proc/TRANREPT.prc` | Report dettaglio transazioni per intervallo date (`DATEPARM`), con totali per carta, pagina e generale, decodifica tipo/categoria |

Copybook: `CVTRA05Y` (transaction), `CVTRA06Y` (daily tran), `CVTRA03Y`/`CVTRA04Y` (tipi/categorie), `CVTRA07Y` (layout report), `CVACT03Y`, mappe `app/cpy-bms/COTRN0*.CPY`.

Nota: anche `app/cbl/COBIL00C.cbl` (CB00, pagamento saldo, scrive una transazione e azzera il saldo del conto) non e' assegnato ad altri workstream: includilo qui come `POST /api/accounts/{acctId}/bill-payment`, usando `AccountBalanceService` di ws-b tramite un'interfaccia (se il modulo account non e' ancora mergiato, definisci la porta in `com.carddemo.transaction.port` e un adapter, segnalandolo in PR).

## Deliverable
- `GET /api/transactions?startAfter=&direction=&size=10` (keyset su `tran_id`), `GET /api/transactions/{tranId}`, `POST /api/transactions` con tutte le validazioni di COTRN02C (importo formato `-99999999.99`, date `YYYY-MM-DD` valide, tipo/categoria esistenti, merchant id numerico) e generazione id atomica (sequence o `SELECT ... FOR UPDATE`, documenta la scelta).
- `DailyTransactionValidator` (CBTRN01C) riusabile da ws-f.
- Job Spring Batch `transactionReportJob` (CBTRN03C) con parametri `startDate`/`endDate`, output testo in `data/out/` con lo stesso layout di `CVTRA07Y` (intestazioni, righe, totali). Endpoint di avvio lo espone ws-h (CORPT00C), qui solo il job.

## Criteri di accettazione
- Test golden del report su un intervallo dei dati seed: righe e totali coincidono con il calcolo atteso.
- Test concorrenza: due `POST` simultanei non generano lo stesso `TRAN-ID`.
- Test di integrazione Testcontainers per API e job.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.transaction` nel modulo `modern/carddemo-transaction`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-transaction/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V500-V599`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-transaction -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-e-transactions`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-e-transactions] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
