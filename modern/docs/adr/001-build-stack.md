# ADR-001 — Build e stack

- **Stato**: accettato (Fase 0, `contracts-v1`)
- **Contesto**: 17 workstream paralleli devono compilare senza toccare il POM padre (piano §3.1–§3.2).
- **Decisione**: Java 21 LTS, Maven multi-modulo con wrapper (`modern/mvnw`, Maven 3.9.9), parent `spring-boot-starter-parent` 3.3.5 (Spring Batch 5, Data JPA, Security 6, Web, Actuator), Flyway + PostgreSQL 16, Testcontainers, LocalStack, ArchUnit, RestAssured, OpenPDF, AWS SDK v2 SQS. Tutti i 21 moduli di §3.2 dichiarati nel padre; versioni solo nel `dependencyManagement` del padre. Qualità: Maven Enforcer (Java 21, Maven ≥ 3.9, `dependencyConvergence`), Checkstyle (validate, vieta `double`/`float`), Spotless + google-java-format (verify), JaCoCo. Profili: default (unit), `parity` (`@Tag("parity")`), `it` (failsafe `*IT`, Docker). `carddemo-app` produce il jar piatto (dipendibile da `carddemo-parity`) e il fat jar `carddemo-app-exec.jar` per l'immagine.
- **Conseguenze**: i workstream aggiungono dipendenze solo nel `pom.xml` del proprio modulo; cambi al padre passano da `CONTRACT-CHANGE`. JRecord è dichiarato ma non risolto dal mirror di Fase 0 (vedi ADR-004).
