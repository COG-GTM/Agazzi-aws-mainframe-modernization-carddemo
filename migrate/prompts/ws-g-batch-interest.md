# Fase 1 - ws-g-batch-interest: interessi, estratti conto e PDF

Package dedicato: `com.carddemo.batch.interest` (modulo `modern/carddemo-batch-interest`).

## Sorgenti legacy da migrare
| Sorgente | Funzione |
|---|---|
| `app/cbl/CBACT04C.cbl` (`app/jcl/INTCALC.jcl`) | Legge `TCATBALF` sequenzialmente per conto; per ogni saldo di categoria cerca il tasso in `DISCGRP` per gruppo del conto + tipo + categoria, con fallback al gruppo `DEFAULT` (`1200-A-GET-DEFAULT-INT-RATE`); interesse mensile = `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200`; scrive una transazione di sistema per conto in `SYSTRAN(+1)` (tipo/categoria interessi, `TRAN-ID` = data parametro + progressivo) e a cambio conto aggiorna l'account (somma interessi al saldo, azzera i cicli). `1400-COMPUTE-FEES` e' vuoto nel COBOL: lascia l'hook, documentalo |
| `app/cbl/CBSTM03A.CBL` + `app/cbl/CBSTM03B.CBL` (`app/jcl/CREASTMT.JCL`) | Estratti conto per carta/conto: CBSTM03B fa l'I/O sui file (TRNXFILE, XREFFILE, CUSTFILE, ACCTFILE) richiamato via CALL da CBSTM03A, che produce sia testo (`STATEMNT.PS`) sia HTML (`STATEMNT.HTML`). Nota il SORT preliminare delle transazioni per carta+id in `TRXFL` |
| `app/jcl/TXT2PDF1.JCL` | Conversione del testo estratto conto in PDF con il tool REXX TXT2PDF |

Copybook: `CVTRA01Y`, `CVTRA02Y`, `CVTRA05Y`, `CVACT01Y`, `CVACT03Y`, `CUSTREC`, `COSTM01.CPY` (layout statement).

## Deliverable
- Job `interestCalculationJob` (parametro `processingDate`, come il PARM di INTCALC): reader `tran_cat_balance` ordinato per conto, lookup tasso con cache e fallback DEFAULT, calcolo `BigDecimal` con la stessa troncatura del `COMPUTE` COBOL, writer delle transazioni interessi su tabella di staging `system_transaction` (concordata con ws-f per COMBTRAN, migrazione nel tuo range) e aggiornamento conto tramite `AccountBalanceService.applyInterest` di ws-b (o porta locale `com.carddemo.batch.interest.port`).
- Job `statementJob`: per ogni carta in xref genera estratto in testo (layout identico a CBSTM03A), HTML e **PDF con libreria Java** (Apache PDFBox 3.x oppure OpenPDF; niente tool esterni) in `data/out/statements/<acctId>-<cardNum>.{txt,html,pdf}`. La separazione CBSTM03A/CBSTM03B diventa servizio di composizione + repository.
- `GET /api/statements/{acctId}` (download PDF) protetto per ruolo: USER solo sui propri conti, ADMIN tutti.

## Criteri di accettazione
- Test golden interessi: per i conti seed, interesse per categoria e totale coincidono con il calcolo manuale da `tcatbal.txt` + `discgrp.txt` (incluso almeno un caso con fallback DEFAULT).
- Test statement: il testo generato per un conto coincide con il layout di `COSTM01`; il PDF e' valido (aperto con PDFBox, contiene nome cliente, conto e totale).
- Test Testcontainers con `JobLauncherTestUtils`.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.batch.interest` nel modulo `modern/carddemo-batch-interest`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-batch-interest/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V700-V799`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-batch-interest -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-g-batch-interest`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-g-batch-interest] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
