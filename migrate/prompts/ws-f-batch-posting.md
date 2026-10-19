# Fase 1 - ws-f-batch-posting: posting giornaliero -> Spring Batch

Package dedicato: `com.carddemo.batch.posting` (modulo `modern/carddemo-batch-posting`).

## Sorgenti legacy da migrare
| Sorgente | Funzione |
|---|---|
| `app/cbl/CBTRN02C.cbl` (`app/jcl/POSTTRAN.jcl`) | Legge `DALYTRAN`, per ogni record valida: carta in xref (reason 100), conto esistente (101), limite di credito superato (102), conto scaduto rispetto alla data della transazione (103). Se valido: aggiorna/crea il saldo per categoria `TCATBALF`, aggiorna il conto (`ACCT-CURR-BAL`, `ACCT-CURR-CYC-CREDIT`/`DEBIT`), scrive in `TRANSACT` con `TRAN-PROC-TS` corrente. Se invalido: scrive il record + motivo in `DALYREJS(+1)`. Return code 4 se ci sono scarti |
| `app/jcl/DALYREJS.jcl` | Definisce la GDG `DALYREJS` (LIMIT 5, SCRATCH) per gli scarti |
| `app/jcl/COMBTRAN.jcl` | SORT di `TRANSACT.BKUP(0)` + `SYSTRAN(0)` (transazioni di sistema generate dagli interessi) per `TRAN-ID` -> `TRANSACT.COMBINED(+1)` e ricaricamento del KSDS `TRANSACT` con IDCAMS REPRO |
| `app/jcl/TRANBKP.jcl` | Backup di `TRANSACT` prima del ricaricamento (dipendenza del flusso giornaliero) |

Copybook: `CVTRA06Y` (daily tran), `CVTRA05Y` (transaction), `CVTRA01Y` (tcatbal), `CVACT01Y`, `CVACT03Y`. Input di esempio: `modern/data/in/dailytran.txt` (copiato dalla fase 0 da `app/data/ASCII/dailytran.txt`).

## Deliverable
- Job `postTransactionsJob` (Spring Batch 5, chunk-oriented): `FlatFileItemReader` a lunghezza fissa con il codec di `carddemo-common` -> `ItemProcessor` con le validazioni 100-103 nello stesso ordine e con gli stessi testi di CBTRN02C -> `ClassifierCompositeItemWriter` (validi: aggiornamento `tran_cat_balance`, `account` tramite `AccountBalanceService` di ws-b o una porta locale `com.carddemo.batch.posting.port`, insert `transaction`; scarti: file `data/out/dalyrejs.G<nnnn>` con layout record + `WS-VALIDATION-TRAILER`, retention 5 generazioni).
- Exit status del job `COMPLETED_WITH_REJECTS` (equivalente RC=4) quando ci sono scarti, con contatori (letti, postati, scartati) nell'`ExecutionContext`, come i DISPLAY finali del COBOL.
- Job `combineTransactionsJob` (COMBTRAN + TRANBKP): backup tabellare/CSV di `transaction`, merge con le transazioni di sistema generate da ws-g (tabella/area di staging definita in accordo con ws-g, documentala), ordinamento per `tran_id`, idempotente.
- Transazionalita': un chunk fallito non deve lasciare saldo conto e tcatbal disallineati; restart sicuro del job.

## Criteri di accettazione
- Test golden su `dailytran.txt` + seed: numero di postati/scartati, saldi finali dei conti e tcatbal identici al risultato atteso del COBOL (calcolalo e documenta il metodo nella PR).
- Test per ognuno dei 4 motivi di scarto e per il restart dopo errore.
- Test Testcontainers con `JobLauncherTestUtils`.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.batch.posting` nel modulo `modern/carddemo-batch-posting`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-batch-posting/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V600-V699`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-batch-posting -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-f-batch-posting`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-f-batch-posting] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
