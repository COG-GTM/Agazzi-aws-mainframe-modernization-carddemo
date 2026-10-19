# Baseline CVE delle dipendenze (rischio accettato con scadenza)

Quando è stato introdotto il gate `modern-security.yml` (Fase 5C, 2026-10-19), lo stack Fase 0
(BOM Spring Boot **3.3.5**, immagine `eclipse-temurin:21-jre`) conteneva **62 CVE HIGH/CRITICAL reali**,
con fix disponibile (Trivy, `--ignore-unfixed`). Non sono falsi positivi.

Per non bloccare le PR in corso dei 17 workstream senza nascondere il problema:

- `modern/security/trivyignore` e `modern/security/dependency-check-suppressions.xml` elencano **solo**
  queste CVE, una per una, con scadenza **2026-11-30** (`exp:` / `until=`). Dopo la scadenza il gate torna rosso.
- Qualsiasi CVE nuova (o le stesse su un'altra dipendenza) fa fallire la CI.
- Dependency-Check (match CPE NVD) riporta molte più CVE di Trivy sulle stesse release, e assegna il CPE
  di una release a tutti i suoi artefatti in modo non deterministico. La sezione 2b del file di soppressione
  le raggruppa quindi per `groupId` + versione esatta (es. `org.springframework/*@6.1.14`), con l'elenco
  esplicito delle CVE: un'altra versione o una CVE nuova fa fallire il gate.
- Il DB NVD arriva dal mirror dei feed JSON pubblicato dal progetto Dependency-Check
  (`dependency-check.github.io/DependencyCheck_Builder/nvd_cache/`): senza `NVD_API_KEY` l'API NVD
  è troppo lenta (job cancellato al 12% dopo 15 min).
- I falsi positivi veri sono tenuti separati, nella sezione "False positives" del file di soppressione, ognuno con motivazione.

## Componenti coinvolti e versione minima corretta

| Componente | Versione attuale | Versione con fix | Gestita da |
|---|---|---|---|
| `tomcat-embed-core` | 10.1.31 | ≥ 10.1.58 | BOM Spring Boot |
| `spring-security-web` / `-crypto` | 6.3.4 | ≥ 6.5.9 | BOM Spring Boot |
| `spring-webmvc`, `spring-core`, `spring-expression` | 6.1.14 | ≥ 6.2.19 | BOM Spring Boot |
| `spring-boot` | 3.3.5 | ≥ 3.5.14 | POM padre |
| `spring-data-commons` | 3.3.5 | ≥ 3.5.12 | BOM Spring Boot |
| `jackson-core` / `-databind` | 2.17.2 | ≥ 2.18.11 | BOM Spring Boot |
| `netty-*` (AWS SDK) | 4.1.114 | ≥ 4.1.137 | BOM AWS SDK / Boot |
| `micrometer-core` | 1.13.6 | ≥ 1.15.12 | BOM Spring Boot |
| `postgresql` (pgjdbc) | 42.7.4 | ≥ 42.7.12 | BOM Spring Boot |
| `pebble` (Go, base image Ubuntu 26.04) | 1.27.1 | 1.27.2 | `eclipse-temurin:21-jre` (rebuild) |

## Correzione richiesta (fuori dal perimetro 5C)

`DEPENDENCY-CHANGE` per Fase 0 / 5A: in `modern/pom.xml` portare `spring-boot-starter-parent` alla
3.5.x più recente (Spring Framework 6.2.x, Security 6.5.x, Tomcat 10.1.5x), allineare
`spring-cloud-aws.version` (3.4.x) e `awssdk.version`, ricostruire l'immagine. Poi svuotare la baseline;
il gate deve restare verde senza voci "accepted-risk".
