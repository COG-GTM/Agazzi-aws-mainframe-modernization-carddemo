# Fase 0 - phase0-foundations: fondamenta della piattaforma Java

Sei il primo workstream della migrazione di CardDemo (COBOL/CICS/VSAM/JCL in `app/`) verso Java 21 + Spring Boot 3 + PostgreSQL. Questa fase e' **sequenziale**: le 8 sessioni della fase 1 partiranno dal tuo branch mergiato, quindi ogni scelta qui (nomi moduli, entity, tipi, convenzioni) diventa contratto per tutti. Package dedicato: `com.carddemo.common` (modulo `modern/carddemo-common`) e `com.carddemo.app` (modulo `modern/carddemo-app`).

## 1. Scaffolding Maven multi-modulo (`modern/`)
- `modern/pom.xml` parent: Java 21, Spring Boot 3.3.x (BOM), Testcontainers BOM, springdoc-openapi, Flyway (`flyway-core` + `flyway-database-postgresql`), Spring Batch, Quartz, JaCoCo, Maven Wrapper (`modern/mvnw`).
- Dichiara **subito tutti** i moduli, cosi' i workstream paralleli non entrano in conflitto sul parent POM. Per ognuno crea `pom.xml`, `package-info.java` e un test di smoke vuoto:

  | Modulo | Package | Workstream |
  |---|---|---|
  | `carddemo-common` | `com.carddemo.common` | fase 0 |
  | `carddemo-auth` | `com.carddemo.auth` | ws-a-auth |
  | `carddemo-account` | `com.carddemo.account` | ws-b-account |
  | `carddemo-card` | `com.carddemo.card` | ws-c-card |
  | `carddemo-customer` | `com.carddemo.customer` | ws-d-customer |
  | `carddemo-transaction` | `com.carddemo.transaction` | ws-e-transactions |
  | `carddemo-batch-posting` | `com.carddemo.batch.posting` | ws-f-batch-posting |
  | `carddemo-batch-interest` | `com.carddemo.batch.interest` | ws-g-batch-interest |
  | `carddemo-reports` | `com.carddemo.reports` | ws-h-reports |
  | `carddemo-ui` (+ `modern/frontend/` placeholder) | `com.carddemo.ui` | ws-i-ui |
  | `carddemo-integration` | `com.carddemo.integration` | ws-j-integration |
  | `carddemo-migration` | `com.carddemo.migration` | ws-k-data-migration |
  | `carddemo-app` | `com.carddemo.app` | fase 0 (wiring finale: ws-j) |

- `carddemo-app`: `@SpringBootApplication(scanBasePackages = "com.carddemo")`, dipende da tutti i moduli di dominio, `application.yml` con profili `local` e `test`, actuator health, `GlobalExceptionHandler` con `ProblemDetail` in `com.carddemo.common.web`.
- Riserva i range Flyway documentandoli in `modern/README.md`: fase 0 V1-V99, ws-a V100-V199, ws-b V200-V299, ws-c V300-V399, ws-d V400-V499, ws-e V500-V599, ws-f V600-V699, ws-g V700-V799, ws-h V800-V899, ws-j V1000-V1099, ws-k V1100-V1199. `spring.flyway.locations=classpath*:db/migration`.

## 2. Entity JPA dai copybook di `app/cpy/`
In `com.carddemo.common.entity` + `com.carddemo.common.repository` (un `JpaRepository` per entity), applicando le regole copybook -> JPA qui sotto:

| Copybook | Record | Tabella | PK (da `DEFINE CLUSTER` in `app/jcl/`) | Indici secondari (AIX) |
|---|---|---|---|---|
| `CVACT01Y` | `ACCOUNT-RECORD` | `account` | `acct_id` (KEYS 11 0, `ACCTFILE.jcl`) | - |
| `CVACT02Y` | `CARD-RECORD` | `card` | `card_num` (KEYS 16 0, `CARDFILE.jcl`) | `card_acct_id` (AIX KEYS 11 16) |
| `CVACT03Y` | `CARD-XREF-RECORD` | `card_xref` | `xref_card_num` (`XREFFILE.jcl`) | `xref_acct_id` (AIX KEYS 11 25) |
| `CVCUS01Y` / `CUSTREC` | `CUSTOMER-RECORD` | `customer` | `cust_id` (KEYS 9 0, `CUSTFILE.jcl`) | - |
| `CVTRA05Y` | `TRAN-RECORD` | `transaction` | `tran_id` (KEYS 16 0, `TRANFILE.jcl`) | `tran_proc_ts` (AIX KEYS 26 304) |
| `CVTRA06Y` | `DALYTRAN-RECORD` | `daily_transaction` (staging input batch) | `dalytran_id` | - |
| `CVTRA01Y` | `TRAN-CAT-BAL-RECORD` | `tran_cat_balance` | `@EmbeddedId` acct+type+cat (`TCATBALF.jcl`) | - |
| `CVTRA02Y` | `DIS-GROUP-RECORD` | `disclosure_group` | `@EmbeddedId` group+type+cat (`DISCGRP.jcl`) | - |
| `CVTRA03Y` | `TRAN-TYPE-RECORD` | `transaction_type` | `tran_type` (`TRANTYPE.jcl`) | - |
| `CVTRA04Y` | `TRAN-CAT-RECORD` | `transaction_category` | `@EmbeddedId` type+cat (`TRANCATG.jcl`) | - |
| `CSUSR01Y` | `SEC-USER-DATA` | `sec_user` | `sec_usr_id` (KEYS 8 0, `DUSRSECJ.jcl`) | - |

Aggiungi le foreign key logiche (card -> account, card_xref -> card/customer/account, transaction -> card via `tran_card_num`) e `@Version` su `account`, `card`, `customer`, `sec_user`. `CVEXPORT` (export multi-record con campi `COMP`) resta fuori dal modello JPA: verra' gestito da ws-h/ws-k.

## 3. Codec copybook e seed SQL
- `com.carddemo.common.codec`: parser di record a lunghezza fissa guidato da layout (offset/len/PIC), decodifica zoned con overpunch, packed decimal `COMP-3`, binario `COMP`, conversione EBCDIC (cp037) -> UTF-8. Test unitari con i primi record di ogni file di `app/data/ASCII` e `app/data/EBCDIC`.
- Seed: migrazioni Flyway `V2__seed_reference.sql` (transaction_type, transaction_category, disclosure_group), `V3__seed_core.sql` (account, card, card_xref, customer, tran_cat_balance), `V4__seed_transactions.sql` (transaction iniziali) e `V5__seed_users.sql`, generate da un generatore riproducibile (`com.carddemo.common.seed.SeedGenerator`, eseguibile con `mvn -pl carddemo-common exec:java`) che legge:
  - i job JCL di load (`ACCTFILE`, `CARDFILE`, `XREFFILE`, `CUSTFILE`, `TRANFILE`, `TRANTYPE`, `TRANCATG`, `DISCGRP`, `TCATBALF`, `DUSRSECJ`) per capire quale dataset carica quale cluster;
  - i dati in `app/data/ASCII/*.txt` (acctdata, carddata, cardxref, custdata, trantype, trancatg, discgrp, tcatbal) e, per `TRANFILE`, `app/data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS.INIT`;
  - gli utenti inline nello step `SYSUT1` di `DUSRSECJ.jcl` (ADMIN001-005, USER0001-0005). Password salvate in chiaro con prefisso `{noop}`: ws-a le convertira' in BCrypt con una sua migrazione. **Eccezione esplicita alla regola `PIC X(n)` -> `VARCHAR(n)`**: `SEC-USR-PWD` e' `PIC X(08)` ma la colonna `sec_user.sec_usr_pwd` deve essere `VARCHAR(100)` (spazio per `{noop}` + 8 caratteri ora e per l'hash `{bcrypt}` di ws-a poi); documentalo nell'entity e copri il seed con un test.
- `app/data/ASCII/dailytran.txt` **non** va in seed: e' l'input del batch di posting (ws-f). Copialo in `modern/data/in/dailytran.txt`.
- Test di integrazione: dopo Flyway i conteggi devono essere 50 account, 50 card, 50 xref, 50 customer, 7 tipi, 18 categorie, 51 disclosure group, 50 tcatbal, 10 utenti (verifica i numeri reali sui file).

## 4. Utility date
`com.carddemo.common.date.CobolDates`: equivalenti di `CSUTLDTC` (validazione data via CEEDAYS, con codici di esito), `CODATECN`/`CSDAT01Y` (data/ora corrente nei formati usati dalle schermate), conversione timestamp `YYYY-MM-DD-HH.MM.SS.ffffff` <-> `LocalDateTime`, `Clock` iniettabile per test deterministici. ws-h-reports estendera' queste utility per la parita' completa con `COBDATFT` (`app/asm/COBDATFT.asm`).

## 5. docker-compose e documentazione
- `modern/docker-compose.yml`: `postgres:16-alpine` (db `carddemo`, healthcheck, volume) + servizio `app` (build da `modern/Dockerfile` multi-stage Temurin 21). `modern/.env.example`.
- `modern/README.md`: come buildare/avviare, mappa moduli/package, range Flyway, convenzioni (copia le Regole comuni sotto), tabella programmi COBOL -> workstream.

## Criteri di accettazione
- `cd modern && ./mvnw verify` verde (tutti i moduli, anche vuoti); test di entity/seed con Testcontainers.
- `docker compose up` avvia Postgres + app, `/actuator/health` = UP, Flyway applica V1-V5.
- Nessun codice di business dei workstream 1-2 implementato qui: solo fondamenta.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.common (+ com.carddemo.app)` nel modulo `modern/carddemo-common`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-common/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V1-V99`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-common -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/phase0-foundations`) e apri **una PR separata** verso il branch base, titolo `[migrate][phase0-foundations] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
