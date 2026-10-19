# Fase 1 - ws-b-account: dominio Conto

Package dedicato: `com.carddemo.account` (modulo `modern/carddemo-account`).

## Sorgenti legacy da migrare
| Programma | Tipo | Funzione |
|---|---|---|
| `app/cbl/COACTVWC.cbl` (CAVW, mappa `COACTVW`) | online | Visualizza conto + cliente associato (via `CARDXREF` AIX su account id) |
| `app/cbl/COACTUPC.cbl` (CAUP, mappa `COACTUP`) | online | Aggiorna conto e dati cliente con validazioni campo per campo e controllo modifiche concorrenti |
| `app/cbl/CBACT01C.cbl` | batch | Legge `ACCTFILE` e scrive file di output (formati diversi, incluso array e record variabile) |
| `app/cbl/CBACT02C.cbl` | batch | Legge e stampa `CARDFILE` |
| `app/cbl/CBACT03C.cbl` | batch | Legge e stampa `CARDXREF` |
| `app/cbl/CBACT04C.cbl` | batch | Calcolo interessi: **la logica del job e' di ws-g-batch-interest**. Qui fornisci solo l'API di servizio sul conto che CBACT04C usa (`1050-UPDATE-ACCOUNT`: azzeramento cicli e somma interessi al saldo) |

Copybook: `CVACT01Y` (account), `CVACT03Y` (xref), `CVCUS01Y` (customer), `CVCRD01Y`, mappe `app/cpy-bms/COACTVW.CPY` e `COACTUP.CPY`. JCL: `ACCTFILE.jcl`, `READACCT.jcl`, `READCARD.jcl`, `READXREF.jcl`.

## Deliverable
- `GET /api/accounts/{acctId}`: vista conto + cliente (risposta equivalente alla mappa COACTVW), 404 se conto o xref assenti con i messaggi di COACTVWC.
- `PUT /api/accounts/{acctId}`: aggiornamento conto + cliente in un'unica transazione, con tutte le validazioni di COACTUPC (date valide via `CobolDates`, importi, stato `Y/N`, SSN, telefono, stato/ZIP USA, FICO 300-850, ecc.) e rilevamento modifiche concorrenti con `@Version` -> 409.
- `AccountBalanceService` (API Java pubblica del modulo, in `com.carddemo.account.service`): `applyPosting(acctId, amount)` (usata da ws-f per aggiornare `ACCT-CURR-BAL`, `ACCT-CURR-CYC-CREDIT`/`DEBIT`) e `applyInterest(acctId, totalInterest)` (usata da ws-g). Documenta il contratto nella PR.
- Batch di lettura/stampa CBACT01C-03C come job Spring Batch (`accountExtractJob`, `cardPrintJob`, `xrefPrintJob`) che producono in `data/out/` file equivalenti all'output COBOL (stessi formati e campi, incluso il record a lunghezza variabile di CBACT01C).

## Criteri di accettazione
- Test golden: per 3 conti dei dati seed, la vista REST coincide campo per campo con i valori di `acctdata.txt`/`custdata.txt`.
- Test negativi per ogni regola di validazione di COACTUPC.
- Job batch testati con `JobLauncherTestUtils` e confronto dell'output atteso.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.account` nel modulo `modern/carddemo-account`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-account/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V200-V299`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-account -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-b-account`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-b-account] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
