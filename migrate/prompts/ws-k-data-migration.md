# Fase 2 - ws-k-data-migration: ETL VSAM -> PostgreSQL con riconciliazione

Package dedicato: `com.carddemo.migration` (modulo `modern/carddemo-migration`).

Obiettivo: strumento ripetibile per migrare i dataset reali di produzione (export VSAM/PS in EBCDIC) nel database PostgreSQL, distinto dai seed Flyway della fase 0 che servono solo per sviluppo e test.

## Sorgenti legacy
| Dataset (in `app/data/EBCDIC/`, nomi `AWS.M2.CARDDEMO.*`) | Copybook | Tabella destinazione |
|---|---|---|
| `ACCTDATA.PS` | `CVACT01Y` | `account` |
| `CARDDATA.PS` | `CVACT02Y` | `card` |
| `CARDXREF.PS` | `CVACT03Y` | `card_xref` |
| `CUSTDATA.PS` | `CVCUS01Y` | `customer` |
| `TCATBALF.PS` | `CVTRA01Y` | `tran_cat_balance` |
| `DISCGRP.PS` | `CVTRA02Y` | `disclosure_group` |
| `TRANTYPE.PS` | `CVTRA03Y` | `transaction_type` |
| `TRANCATG.PS` | `CVTRA04Y` | `transaction_category` |
| `DALYTRAN.PS`, `DALYTRAN.PS.INIT` | `CVTRA06Y` / `CVTRA05Y` | `daily_transaction` / `transaction` |
| `USRSEC.PS` | `CSUSR01Y` | `sec_user` |

Riferimenti: definizioni cluster e lunghezze record nei JCL di `app/jcl/` (`ACCTFILE`, `CARDFILE`, `CUSTFILE`, `XREFFILE`, `TRANFILE`, `DUSRSECJ`, ...), `app/catlg/LISTCAT.txt`, `app/ctl/REPROCT.ctl`, copie ASCII in `app/data/ASCII/` utili per confronto.

## Deliverable
- Applicazione Spring Batch CLI (`java -jar carddemo-migration.jar --source=<dir> --encoding=cp037|ascii --mode=full|delta --dry-run`) con un job per dataset: lettura record a lunghezza fissa (RECFM FB, LRECL dai JCL) via codec di `carddemo-common` (EBCDIC cp037, zoned overpunch, `COMP-3`, `COMP`), mapping copybook -> entity, scrittura batch (JDBC batch insert o `COPY`), ordine di caricamento che rispetta le FK (riferimento -> customer -> account -> card -> xref -> tcatbal -> transaction -> utenti).
- Gestione errori: record non decodificabili o che violano vincoli -> tabella `migration_error` (dataset, numero record, offset, raw hex, motivo) senza bloccare il job; soglia di errore configurabile.
- Password `USRSEC`: hash BCrypt in ingresso (mai in chiaro nel DB).
- **Riconciliazione** (`reconciliationJob` + report `data/out/reconciliation-<timestamp>.{md,csv}`): per ogni dataset conteggio record sorgente vs tabella, somme di controllo sui campi monetari (`ACCT-CURR-BAL`, `ACCT-CREDIT-LIMIT`, `TRAN-CAT-BAL`, `TRAN-AMT`, ...), hash per record chiave->contenuto normalizzato con elenco differenze, integrita' referenziale (carte senza conto, xref orfane). Exit code != 0 se la riconciliazione fallisce.
- Idempotenza: rilancio `full` su DB gia' popolato -> truncate controllato o upsert; tabella `migration_run` con esito, durata e contatori (migrazione Flyway nel tuo range).
- Runbook `modern/docs/data-migration.md`: prerequisiti, export da mainframe (REPRO in PS), trasferimento binario, esecuzione, rollback, interpretazione del report.

## Criteri di accettazione
- Migrazione completa dei file EBCDIC di esempio su un DB Testcontainers vuoto (solo schema, senza seed) con riconciliazione al 100%: conteggi e somme di controllo identici, contenuti uguali a quelli dei file ASCII corrispondenti.
- Test con file corrotto: record errati finiscono in `migration_error`, report evidenzia la differenza, exit code != 0.

## Regole comuni (vincolanti per ogni workstream)

### Stack e struttura
- Java 21, Spring Boot 3.3.x, Maven multi-modulo con parent `modern/pom.xml`, PostgreSQL 16, Flyway, Lombok vietato (usa `record` per DTO e classi esplicite per entity).
- Package dedicato: **tutto** il codice Java di questo workstream sta sotto `com.carddemo.migration` nel modulo `modern/carddemo-migration`. Non creare ne' modificare classi in package/moduli di altri workstream.
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
- Le migrazioni di questo workstream vanno in `modern/carddemo-migration/src/main/resources/db/migration/` e usano **solo** il range di versioni riservato: `V1100-V1199`. Mai modificare migrazioni gia' esistenti (V1-V99 sono della fase 0).

### Test
- JUnit 5 + **Testcontainers** PostgreSQL (`postgres:16-alpine`, `@ServiceConnection`). Niente H2 o DB in-memory.
- Unit test per ogni regola di business portata dal COBOL; test di integrazione per REST (`MockMvc`) e batch (`JobLauncherTestUtils`) contro il DB Testcontainers con i seed Flyway.
- Almeno un test "golden" che confronta l'output Java con il comportamento atteso del programma COBOL sui dati di `app/data/ASCII`.
- `mvn -f modern/pom.xml -pl carddemo-migration -am verify` deve passare; copertura JaCoCo >= 80% sulle classi `service` del modulo. Includi l'output dei test nella PR.

### Consegna
- Lavora **solo** sul branch indicato nel contesto (`migrate/ws-k-data-migration`) e apri **una PR separata** verso il branch base, titolo `[migrate][ws-k-data-migration] <sintesi>`. Non fare merge e non toccare altri branch.
- Descrizione PR: tabella di mapping COBOL -> Java (programma/paragrafo -> classe/metodo), endpoint/job creati, migrazioni Flyway aggiunte, evidenza dei test (comando + esito), sezione "Modifiche condivise" e "Punti aperti / differenze dal COBOL".
- Se trovi un'ambiguita' nel sorgente COBOL, scegli il comportamento fedele al COBOL e documentalo nella PR invece di bloccarti.
