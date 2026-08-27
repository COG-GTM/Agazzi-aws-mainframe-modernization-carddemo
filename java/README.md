# CardDemo Java modernization

This directory contains the Phase 0, Phase 1, Phase 2.1, and Phase 2.2 Java 17 foundation for the
AWS CardDemo application. The existing COBOL, copybooks, JCL, and sample data
under `app/` remain unchanged.

## Modules

| Module | Phase 0/1 responsibility | Mainframe sources |
| --- | --- | --- |
| `carddemo-domain` | JPA entities, repositories, Flyway schema, and shared COBOL compatibility utilities | `CVACT01Y`–`CVACT03Y`, `CVCUS01Y`, `CVTRA01Y`–`CVTRA06Y`, `CSUTLDTC`, `COBDATFT`, `COBSWAIT` |
| `carddemo-data-migration` | Fixed-width ASCII sample-data parsers and idempotent CLI loader | `app/data/ASCII`, copybook layouts |
| `carddemo-batch` | Spring Batch ports of transaction, interest, report, statement, and sequential display programs | `CBACT01C`–`CBACT04C`, `CBTRN01C`–`CBTRN03C`, `CBSTM03A`/`CBSTM03B`, `CBCUS01C` |
| Online CICS, messaging, and database jobs | Not implemented in this handoff | Phases 2–5 |

## Build

Use JDK 17:

```bash
cd java
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B verify
```

Unit tests use Surefire (`*Test`). The PostgreSQL integration test uses
Testcontainers (`*IT`) and is guarded so it skips when Docker is unavailable.
With Docker available, it runs against PostgreSQL 16. H2 in PostgreSQL mode is
used for the Flyway plus `hibernate.ddl-auto=validate` schema test.

## Data loader

The executable Spring Boot jar is created with the `exec` classifier:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B -f java/pom.xml package
java -jar java/carddemo-data-migration/target/carddemo-data-migration-0.1.0-SNAPSHOT-exec.jar \
  --loader.input-dir=app/data/ASCII
```

The default input directory is `app/data/ASCII`. The loader processes files in
foreign-key-safe order, reports one count per file, and uses explicit COBOL
identifiers for idempotent persistence. Add `--reset` to clear the loaded
tables before loading.

## Phase 2.1 transaction posting

`postTransactionsJob` maps the `STEP15 EXEC PGM=CBTRN02C` step in
`app/jcl/POSTTRAN.jcl`. It reads `daily_transaction` in daily-transaction-ID
order and validates each record in COBOL order:

| Code | Validation | Description |
| --- | --- | --- |
| 100 | Card cross-reference is missing | `INVALID CARD NUMBER FOUND` |
| 101 | Account is missing | `ACCOUNT RECORD NOT FOUND` |
| 102 | Credit limit is below current-cycle balance plus amount | `OVERLIMIT TRANSACTION` |
| 103 | Account expiration precedes the transaction date | `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` |

The first failure rejects the record. Valid records are copied to
`transaction_record`, update the account current balance and signed
current-cycle credit/debit fields, and update or create the
`tran_cat_balance` row keyed by account, type, and category. All monetary
calculations use `BigDecimal` with scale 2. Expiration dates are compared as
fixed-width strings, as in the COBOL.

Rejects are stored in `daily_transaction_reject`. Their raw record is
re-serialized to exactly 350 characters using the declarative daily
transaction layout; together with the 80-character validation trailer this
matches the `DALYREJS` JCL `LRECL=430` record. The job logs processed and
rejected counts. A run with one or more rejects has exit code
`COMPLETED_WITH_REJECTS` while retaining Spring Batch `BatchStatus.COMPLETED`;
the executable returns process code 4, mirroring `RETURN-CODE 4` in
`CBTRN02C`. A completed run without rejects returns 0 and an exception returns
1. The posting step uses chunk size one to mirror the record-at-a-time COBOL
loop.

Run the batch executable after loading the sample data:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 mvn -B -f java/pom.xml package
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --spring.datasource.url=jdbc:postgresql://localhost/carddemo \
  --spring.datasource.username=carddemo \
  --spring.datasource.password=carddemo \
  --run-post-transactions
```

## Phase 2.2 jobs

The generic launcher accepts `--job.name=<name>` and forwards other options as
job parameters:

```bash
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=printAccountsJob --output=target/accounts.txt
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=printCardsJob --output=target/cards.txt
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=printCardXrefJob --output=target/card-xref.txt
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=printCustomersJob --output=target/customers.txt
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=readDailyTransactionsJob --output=target/daily-read.txt
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=interestCalculationJob --interest-date=2022071800
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=transactionReportJob --start-date=2022-07-01 \
  --end-date=2022-07-31 --output=target/transactions.txt
java -jar java/carddemo-batch/target/carddemo-batch-0.1.0-SNAPSHOT-exec.jar \
  --job.name=createStatementsJob --statement-text=target/statements.txt \
  --statement-html=target/statements.html
```

The composite JCL stream replacements are launched the same way, for example
`--job.name=POSTTRAN`, `--job.name=INTCALC`, `--job.name=CREASTMT`,
`--job.name=TRANREPT`, or `--job.name=COMBTRAN`.

The sequential jobs are `printAccountsJob`, `printCardsJob`,
`printCardXrefJob`, and `printCustomersJob`. They use primary-key order and
`FlatFileItemWriter`. `readDailyTransactionsJob` performs the CBTRN01C
xref/account/card/customer/transaction lookups and reports misses without
aborting. `interestCalculationJob` uses `(balance * rate) / 1200` with
`BigDecimal` scale 2 and `RoundingMode.DOWN`, which is truncation toward zero
for COBOL COMPUTE without ROUNDED. `1400-COMPUTE-FEES` is an explicit no-op.
`createStatementsJob` injects `CobolStatementDataService`, the Java service
equivalent of CBSTM03B's open/read/keyed-read/close contract, and creates both
text and HTML artifacts. CREASTMT's temporary keyed transaction copy is
represented by a relational card-number query.

### JCL stream mapping

| JCL member | Spring Batch job | COBOL programs | Replacement notes |
| --- | --- | --- | --- |
| `POSTTRAN.jcl` | `POSTTRAN` | `CBTRN02C` | Direct relational reader |
| `INTCALC.jcl` | `INTCALC` | `CBACT04C` | `interest-date` replaces PARM-DATE |
| `CREASTMT.JCL` | `CREASTMT` | `CBSTM03A` + `CBSTM03B` | IDCAMS/IEFBR14 become writer lifecycle; SORT is relational ordering |
| `TRANREPT.jcl` | `TRANREPT` | `CBTRN03C` | REPROC/SORT become relational filtering and ordering |
| `COMBTRAN.jcl` | `COMBTRAN` | SORT + IDCAMS | Combined transaction master is already relational; explicit no-op step |

GDG generations are not present in Phase 1 inputs and are represented by
ordinary job parameters rather than silently created. `WAITSTEP` is replaced
by Spring Batch sequencing without an artificial sleep.

Online CICS programs, messaging, DB2 integration, and other Phase 3–5 jobs
remain deferred.

## COBOL compatibility decisions

Packed/zoned decimal amounts are decoded into `BigDecimal` with scale 2;
floating-point types are not used. COBOL date and timestamp fields remain
fixed-width strings rather than being converted to Java date types. The
formatter supports the observed `YYYYMMDD` and `YYYY-MM-DD` conversions.

`CobolWait` is a no-op by default. Callers may opt into real centisecond
sleeping for local demonstrations. The no-op default avoids carrying
mainframe/JCL pacing into application startup; Spring Batch sequencing will
replace the original wait steps in Phase 2.

The loader rejects values that cannot be represented at the required scale
instead of silently applying floating-point rounding. Exact CEEDAYS feedback
behavior that depends on the mainframe runtime is documented in
`CobolDateValidator`.
