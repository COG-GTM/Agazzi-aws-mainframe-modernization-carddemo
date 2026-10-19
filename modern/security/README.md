# carddemo-security (Fase 5C)

Auto-configurazione additiva alla `SecurityFilterChain` di O1 (non la sostituisce):

| Componente | Cosa fa |
|---|---|
| `SecurityHeadersFilter` | CSP, `X-Frame-Options`, `nosniff`, `Referrer-Policy`, `Permissions-Policy`, COOP/CORP, `no-store` su `/api/**`, HSTS sulle richieste TLS |
| `StructuredAuditEventRepository` | Eventi Boot `AUTHENTICATION_SUCCESS/FAILURE`, `AUTHORIZATION_FAILURE` → logger `carddemo.audit` |
| `AccessAuditFilter` + `AuditCategory` | Un evento `ACCESS` per chiamata API (SIGNON/ADMIN/UPDATE/READ/JOB), solo AID `action` dal body |
| `SecurityEventAuditListener` | `LOGOUT`, `SESSION_ROTATED` |
| Spring Cloud AWS Secrets Manager | `spring.config.import=aws-secretsmanager:` nel profilo `aws-sm` |

Configurazione e profili in `carddemo-app/src/main/resources`: `application.yml` (cookie, errori, actuator),
`application-aws.yml`, `application-aws-sm.yml`, `application-tls.yml`, `logback-spring.xml` (JSON in `aws`).
Proprietà: `carddemo.security.headers.*`, `carddemo.security.audit.*` (`SecurityHardeningProperties`).

## Build e test

Il modulo è **fuori dal reactor** (POM padre e `carddemo-app/pom.xml` non sono nel perimetro 5C):

```bash
cd modern
./mvnw -B install -DskipTests
./mvnw -B -f security/pom.xml spotless:apply verify -Pit   # unit + IT (Testcontainers PostgreSQL, LocalStack)
```

## Integrazione (5A)

1. `modern/pom.xml`: `<module>security</module>`.
2. `carddemo-app/pom.xml`: dipendenza `com.carddemo:carddemo-security`.
3. Togliere `-f security/pom.xml` da `modern-security.yml` (sarà coperto dal reactor).

## File di scansione

- `gitleaks.toml` — allowlist motivata (report surefire storici).
- `dependency-check-suppressions.xml`, `trivyignore` — falsi positivi + baseline CVE a scadenza, vedi
  [`docs/security/dependency-baseline.md`](../docs/security/dependency-baseline.md).
- `codeql-config.yml` — CodeQL `security-extended` solo su `modern/`.
