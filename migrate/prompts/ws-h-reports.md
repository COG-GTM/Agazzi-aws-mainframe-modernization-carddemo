# Fase 1 - ws-h-reports: report, export/import e utility data

Package dedicato: `com.carddemo.reports` (modulo `modern/carddemo-reports`).

## Sorgenti legacy da migrare
| Sorgente | Funzione |
|---|---|
| `app/cbl/CORPT00C.cbl` (CR00, mappa `CORPT00`) | Richiesta report transazioni: mensile, annuale o intervallo personalizzato; valida le date con `CSUTLDTC`; sottomette il job `TRANREPT` scrivendo JCL su TDQ `JOBS` (internal reader) |
| `app/jcl/TRANREPT.jcl`, `app/proc/TRANREPT.prc` | Backup + filtro per data (SORT) + `CBTRN03C`. Il job `transactionReportJob` e' implementato da ws-e-transactions: qui ne fai l'orchestrazione |
| `app/cbl/CBEXPORT.cbl` (`app/jcl/CBEXPORT.jcl`) | Export per migrazione di filiale: legge customer, account, xref, transaction, card e scrive un file multi-record `EXPORT.DATA` (`app/cpy/CVEXPORT.cpy`: tipo record, timestamp, sequenza `COMP`, branch, region, dati in `REDEFINES`) |
| `app/cbl/CBIMPORT.cbl` (`app/jcl/CBIMPORT.jcl`) | Import del file di export: smista per tipo record su file separati e scrive gli errori in `IMPORT.ERRORS` |
| `app/asm/COBDATFT.asm` | Routine assembler di conversione formati data |
| `app/cbl/CSUTLDTC.cbl` (+ `app/cpy/CSUTLDPY.cpy`, `CSUTLDWY.cpy`) | Validazione data via CEEDAYS con messaggi/codici di esito |

Dati di esempio: `app/data/EBCDIC/AWS.M2.CARDDEMO.EXPORT.DATA.PS`.

## Deliverable
- `POST /api/reports/transactions` (body: `type = MONTHLY|YEARLY|CUSTOM`, `startDate`, `endDate`) con le validazioni e i messaggi di CORPT00C -> lancio asincrono di `transactionReportJob` (ws-e) tramite `JobLauncher`/`JobOperator`, risposta 202 con id esecuzione; `GET /api/reports/executions/{id}` (stato) e `GET /api/reports/executions/{id}/file` (download). La sottomissione via TDQ/internal reader sparisce: documentalo.
- Job `customerExportJob` (CBEXPORT) e `customerImportJob` (CBIMPORT): formato file compatibile byte-per-byte con `CVEXPORT` (campi `COMP` big-endian, `REDEFINES` per tipo record, opzione EBCDIC cp037 o ASCII), con il codec di `carddemo-common`. L'import deve leggere il file EBCDIC di esempio e produrre gli stessi conteggi per tipo record e lo stesso file errori.
- Parita' completa delle utility data: estendi `com.carddemo.common.date.CobolDates` (in modo additivo) o crea `com.carddemo.reports.date` con gli equivalenti di `COBDATFT` (tutte le conversioni di formato supportate dall'assembler) e `CSUTLDTC` (stessi codici di feedback e messaggi, inclusi anno bisestile e date fuori range). Tabella di test parametrica derivata dai casi del sorgente.

## Criteri di accettazione
- Round-trip: export dal DB seed -> import -> stessi record; import del file EBCDIC di esempio senza errori inattesi.
- Test parametrici `COBDATFT`/`CSUTLDTC` con almeno 30 casi (validi, invalidi, bisestili).
- Test di integrazione Testcontainers per API report (lancio, stato, download) e per i job.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.reports` nel modulo `modern/carddemo-reports`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-reports/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V800-V899`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-reports -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-h-reports`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-h-reports] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
