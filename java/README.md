# CardDemo Java modernization

This directory contains the Phase 0 and Phase 1 Java 17 foundation for the
AWS CardDemo application. The existing COBOL, copybooks, JCL, and sample data
under `app/` remain unchanged.

## Modules

| Module | Phase 0/1 responsibility | Mainframe sources |
| --- | --- | --- |
| `carddemo-domain` | JPA entities, repositories, Flyway schema, and shared COBOL compatibility utilities | `CVACT01Y`–`CVACT03Y`, `CVCUS01Y`, `CVTRA01Y`–`CVTRA06Y`, `CSUTLDTC`, `COBDATFT`, `COBSWAIT` |
| `carddemo-data-migration` | Fixed-width ASCII sample-data parsers and idempotent CLI loader | `app/data/ASCII`, copybook layouts |
| `carddemo-batch` | Spring Batch application skeleton | Phase 2 |
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
