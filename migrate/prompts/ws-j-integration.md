# Fase 2 - ws-j-integration: scheduler Control-M/CA7 -> Quartz e wiring dei moduli

Package dedicato: `com.carddemo.integration` (modulo `modern/carddemo-integration`); sei anche owner del wiring finale in `modern/carddemo-app`.

## Sorgenti legacy da migrare
| Sorgente | Contenuto |
|---|---|
| `app/scheduler/CardDemo.controlm` | Folder Control-M (es. `DAILY-TransactionBackup`) con job `CLOSEFIL`, `TRANBKP`, `COMBTRAN`, `DISCGRP`, `INTCALC`, `TRANEXTR`, `MNTTRDB2`, `OPENFIL`, `WAITSTEP`, condizioni `INCOND`/`OUTCOND`, orari (`TIMETO`), `MAXRERUN` |
| `app/scheduler/CardDemo.ca7` | Definizioni CA7 con trigger tra job (es. `CLOSEFIL` -> `CBPAUP0J`, `POSTTRAN`, `CREASTMT`, `TXT2PDF1`, `READ*`, `TCATBALF`, `TRANCATG`, `TRANTYPE`, `PRTCATBL`) |
| `scripts/run_full_batch.sh`, `scripts/run_posting.sh`, `scripts/run_interest_calc.sh` | Sequenza operativa del ciclo batch completo |
| `app/jcl/CLOSEFIL.jcl`, `OPENFIL.jcl`, `WAITSTEP.jcl`, `app/cbl/COBSWAIT.cbl` | Chiusura/apertura file CICS durante il batch e attesa |

## Deliverable
- Inventario in `modern/docs/batch-schedule.md`: per ogni job Control-M/CA7 -> job Spring Batch Java (owner workstream), dipendenze, orario, rerun; job senza equivalente (es. `MNTTRDB2`, `TRANEXTR`, `CBPAUP0J` delle estensioni DB2/IMS opzionali) marcati esplicitamente come fuori scope.
- Quartz (`spring-boot-starter-quartz`, JobStore JDBC su PostgreSQL con tabelle via Flyway nel tuo range) che orchestra i job Spring Batch dei moduli come flusso giornaliero: `maintenance-mode on` (equivalente CLOSEFIL: blocca le scritture online con un flag letto da un filtro) -> backup transazioni -> `postTransactionsJob` (ws-f) -> `interestCalculationJob` (ws-g) -> `combineTransactionsJob` (ws-f) -> `statementJob` (ws-g) -> `maintenance-mode off` (OPENFIL). Usa un `Flow`/job orchestratore Spring Batch con stop su errore e retry pari a `MAXRERUN`.
- Cron configurabili da `application.yml`, endpoint admin `POST /api/admin/batch/{flow}/run`, `GET /api/admin/batch/executions` (solo ADMIN).
- Wiring in `carddemo-app`: verifica che tutti i moduli della fase 1 siano dipendenze, che le porte definite localmente dai workstream (`*.port`) siano collegate agli adapter reali (es. `AccountBalanceService` di ws-b in posting e interessi), eliminando duplicati/stub. Profili `local`, `docker`, `test`; aggiorna `modern/docker-compose.yml` (app + db + eventuale frontend servito da `carddemo-ui`).
- Test end-to-end di integrazione (Testcontainers, `carddemo-app`): avvio contesto completo, login, esecuzione del flusso batch giornaliero completo sui dati seed + `dailytran.txt`, verifiche su saldi, transazioni combinate e statement generati.

## Criteri di accettazione
- `cd modern && ./mvnw verify` verde su tutto il reactor.
- `docker compose up` -> app avviata, flusso batch eseguibile da endpoint e da cron.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.integration` nel modulo `modern/carddemo-integration`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-integration/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V1000-V1099`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-integration -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-j-integration`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-j-integration] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
