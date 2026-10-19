# CONVENTIONS — modern/

## Struttura
- Un modulo Maven per dominio (piano §3.2); package base `com.carddemo.<area>[.<modulo>]` (es. `com.carddemo.batch.posting`, `com.carddemo.online.account`).
- Sotto-package consigliati: `job` (config Spring Batch), `step`, `service` (logica paragrafi COBOL), `web` (controller), `mapper`, `config`.
- Confini (ArchUnit, `carddemo-app/src/test/java/com/carddemo/app/ArchitectureTest.java`): `batch-*`, `online-*`, `ext-*` dipendono solo da `contracts`, `domain`, `copybook`, `platform`, `online-common`; mai tra loro; i moduli condivisi non dipendono dai moduli di dominio; `contracts` è senza Spring/JPA; nessun `LocalDateTime.now()`/`LocalDate.now()`/`System.currentTimeMillis()` fuori da `platform.time` (usare `BusinessClock`).
- Wiring: ogni modulo espone i propri bean con `@AutoConfiguration` (file `META-INF/spring/...AutoConfiguration.imports`) o `@Configuration` sotto `com.carddemo` (scansionato da `CardDemoApplication`). I fallback di Fase 0 sono `@ConditionalOnMissingBean`: basta dichiarare un bean della stessa porta.

## Naming
- Job Spring Batch = nome JCL (`POSTTRAN`), step = nome step JCL (`STEP15`).
- Classi: `<Programma>Service` (es. `Cbtrn02cService`), `<Mappa>Controller`, `<Programma>ParityTest` (golden), `<Paragrafo>Test` (unit, es. `Process1500ValidateTranTest`), `*IT` per test con Docker.
- Commenti con riferimento al paragrafo COBOL: `// CBTRN02C 1500-VALIDATE-TRAN`.

## Regole numeriche e di formato (§3.4)
1. `PIC S9(n)V9(m)` → `BigDecimal` scala `m`; mai `double`/`float` (Checkstyle).
2. `COMPUTE` senza `ROUNDED` → `CobolMath.store(v, n, m, TRUNCATE)` (`RoundingMode.DOWN`); con `ROUNDED` → `ROUNDED` (`HALF_UP`).
3. MOVE in PIC più piccolo → `CobolMath.fit` (tronca le cifre alte); `ON SIZE ERROR` → `CobolMath.sizeError`.
4. `PIC X(n)` resta padded nel dominio e nel DB; trim solo nei DTO di presentazione.
5. Date/ora solo via `BusinessClock` (fisso nei test: `FixedBusinessClock`, profilo `test` = `2022-07-18T12:00`).
6. Ordinamenti che dipendono da EBCDIC vanno marcati nel golden.

## Test
- Default `./mvnw verify`: unit + ArchUnit + JaCoCo. `-Pparity`: `@Tag("parity")` (gate di parità). `-Pit`: `*IT` con Testcontainers (PostgreSQL 16, LocalStack).
- Scenari in attesa di un altro workstream: `@Tag("pending-<ws>")` (esclusi da surefire/failsafe) + `status: pending-<ws>` nello YAML.
- Copertura ≥ 80% sui moduli di dominio.

## Database
- Flyway: solo nel proprio range (§4.2), file in `src/main/resources/db/migration` del proprio modulo; V1–V99 sono di Fase 0 (V1 baseline, V2 seed in `db/seed`, V3 Spring Batch).
- Ogni tabella aggiornabile ha `version BIGINT` (optimistic lock `@Version`).

## Commit e PR
- Commit: `<ws-id>: <imperativo breve>` (es. `b1-posting: POSTTRAN step STEP15`).
- PR verso il branch di fase (`migration/phase-1-4`), mai `main`; descrizione con: perimetro, test (unit/parity/IT), golden, sezioni `CONTRACT-CHANGE:` e `PARITY-EXCEPTION:` se servono, messaggio finale §4.5.
- Prima del push: `./mvnw -B spotless:apply verify`.
