# OWASP ASVS 4.0.3 — Livello 2 — CardDemo Java 21

Checklist dei controlli ASVS L2 rilevanti, mappati su moduli `modern/` ed endpoint di
`carddemo-contracts/src/main/resources/openapi/openapi.yaml` (contratto `contracts-v1`).

Legenda stato: ✅ implementato e testato oggi su `migration/phase-5` · 🟡 parziale / solo configurazione ·
⏳ da implementare nel workstream indicato (requisito vincolante per il merge in Fase 5) · ⚠️ gap noto con piano.
"Verifica" indica il test che lo dimostra (esistente) o che va scritto.

## Mappa endpoint → workstream → controlli chiave

| Endpoint (`openapi.yaml`) | Tag | Transazione / programma | Modulo (owner) | Categoria audit | Controlli chiave |
|---|---|---|---|---|---|
| `GET/POST /api/v1/screens/cosgn00` | o1-auth | CC00 COSGN00C | `carddemo-online-auth` (O1) | SIGNON | V2.1, V2.2, V2.4, V3.2, V3.4, V7.2 |
| `GET/POST /api/v1/screens/comen01` | o1-auth | CM00 COMEN01C | `carddemo-online-auth` (O1) | NAVIGATION | V3.3 (PF3 logout), V4.1 (opzioni per ruolo) |
| `GET/POST /api/v1/screens/coadm01` | o1-auth | CA00 COADM01C | `carddemo-online-auth` (O1) | ADMIN | V4.1.1, V4.1.3, V4.3.1 (PE-005) |
| `GET/POST /api/v1/screens/coactvw` | o2-account | CAVW COACTVWC | `carddemo-online-account` (O2) | READ | V4.2.1 IDOR, V8.3 masking SSN (PE-012) |
| `GET/POST /api/v1/screens/coactup` | o2-account | CAUP COACTUPC | `carddemo-online-account` (O2) | UPDATE | V4.1.3, V4.2.1, V5.1, V11.1 (PE-011) |
| `GET/POST /api/v1/screens/cocrdli` | o3-card | CCLI COCRDLIC | `carddemo-online-card` (O3) | READ | V4.2.1 filtro per cliente |
| `GET/POST /api/v1/screens/cocrdsl` | o3-card | CCDL COCRDSLC | `carddemo-online-card` (O3) | READ | V4.2.1, V8.3 (PE-009) |
| `GET/POST /api/v1/screens/cocrdup` | o3-card | CCUP COCRDUPC | `carddemo-online-card` (O3) | UPDATE | V4.1.3, V4.2.1, V11.1.6 lock ottimistico (PE-010) |
| `GET/POST /api/v1/screens/cotrn00` | o4-transaction | CT00 COTRN00C | `carddemo-online-transaction` (O4) | READ | V4.2.1 filtro per cliente |
| `GET/POST /api/v1/screens/cotrn01` | o4-transaction | CT01 COTRN01C | `carddemo-online-transaction` (O4) | READ | V4.2.1, V8.3 masking PAN (PE-008) |
| `GET/POST /api/v1/screens/cotrn02` | o4-transaction | CT02 COTRN02C | `carddemo-online-transaction` (O4) | UPDATE | V4.1.3 solo admin (PE-007), V5.1 |
| `GET/POST /api/v1/screens/cobil00` | o4-transaction | CB00 COBIL00C | `carddemo-online-transaction` (O4) | UPDATE | V4.2.1 IDOR (PE-013), V11.1.4 anti-replay/doppio pagamento |
| `GET/POST /api/v1/screens/corpt00` | o4-transaction | CR00 CORPT00C | `carddemo-online-transaction` (O4) + B3/B5 | JOB | V4.1, V11.1.4 limiti, V12.x output report |
| `GET /api/v1/jobs/{executionId}` | o4-transaction | stato job TRANREPT | O4 + `carddemo-batch-orchestration` (B5) | JOB | V4.2.1 (solo job propri o admin) |
| `GET/POST /api/v1/screens/cousr00..03` | o5-useradmin | CU00–CU03 COUSR00C–03C | `carddemo-online-useradmin` (O5) | ADMIN | V4.1.1, V4.1.3, V2.1/V2.4 (PE-002..004) |
| `GET/POST /api/v1/screens/copau00`, `copau01` | x1-authorization | CPVS/CPVD COPAUS0C/1C | `carddemo-ext-authorization` (X1) | READ / UPDATE (frode PF5) | V4.2.1, V8.3 |
| `GET/POST /api/v1/screens/cotrtli`, `cotrtup` | x2-trantype | CTLI/CTTU COTRTLIC/UPC | `carddemo-ext-trantype` (X2) | ADMIN | V4.1.3 solo admin, V5.3.4 query parametrizzate |
| `GET /api/v1/ext/mqinquiry/system-date` | optional-extension | CDRD CODATE01 | `carddemo-ext-mqinquiry` (X3) | READ | V4.1 autenticazione macchina |
| `GET /api/v1/ext/mqinquiry/accounts/{acctId}` | optional-extension | CDRA COACCT01 | `carddemo-ext-mqinquiry` (X3) | READ | V4.2.1 (PE-014), V13.1 |
| `GET/POST /actuator/maintenance` | platform | — | `carddemo-app` (5A/5B) | ADMIN | V4.1.3, V14.3 |
| `/actuator/health`, `/actuator/info`, `/openapi/**` | platform | — | `carddemo-app` (Fase 0) | — | pubblici; in `aws` solo `health`, senza dettagli |

`AuditCategoryTest` fallisce se viene aggiunto a `openapi.yaml` un path senza categoria di audit.

## V2 — Autenticazione

| ID | Requisito (sintesi) | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 2.1.1 | Password ≥ 12 caratteri | ⚠️ | O1 + O5 | Il COBOL ha `SEC-USR-PWD PIC X(8)`. Proposta PE-006: per i nuovi utenti/cambi password accettare ≥ 12 (colonna V1 `VARCHAR(100)` già idonea all'hash); mantenere 8 solo per gli account migrati con reset forzato. |
| 2.1.7 | Rifiuto password compromesse/di default | ⏳ | O1 + O5 | Rifiutare `PASSWORD` e la lista nota (fix `usrsec-seed-creds`). Test: `ADMIN001/PASSWORD` → 401 nel profilo `aws`. |
| 2.2.1 | Anti-automazione (rate limit / lockout) | ⏳ | O1 | Lockout progressivo per userid + limit per IP; evento `AUTHENTICATION_FAILURE` già prodotto (5C). |
| 2.2.2 / 2.2.3 | Messaggi generici, notifiche | ⏳ | O1 | Stesso messaggio per utente inesistente / password errata (COSGN00C distingue: PE da proporre se si uniforma). |
| 2.4.1 | Hash adattivo (bcrypt/argon2) | 🟡 | O1 + 1B | `LegacyUserCredentialService` accetta `{bcrypt}` ma anche testo in chiaro del seed (PE-001). 1B deve migrare a `{bcrypt}`; in `aws` nessun seed. |
| 2.5.x | Recupero credenziali | n/a | — | Non esiste self-service in CardDemo; reset solo da admin (COUSR02). |
| 2.10.1–2.10.4 | Segreti di servizio non nel codice | ✅ | 5C | DB da env/Secrets Manager (`application-aws.yml`, `application-aws-sm.yml`); gitleaks in CI; `AwsProfileIT` prova la lettura da Secrets Manager. |

## V3 — Gestione della sessione

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 3.1.1 | Session id mai in URL | ✅ | 5C | `server.servlet.session.tracking-modes=cookie`. |
| 3.2.1 | Nuovo id al login | ⏳ | O1 | `sessionFixation().changeSessionId()` (default Spring Security) nella catena O1; evento `SESSION_ROTATED` già auditato da 5C. Test: id prima/dopo COSGN00 diversi. |
| 3.3.1 | Logout invalida la sessione | ⏳ | O1 | PF3 su COSGN00/COMEN01 → `logout()`; evento `LOGOUT` già auditato. |
| 3.3.2 | Timeout di inattività | ✅ | 5C | `server.servlet.session.timeout=15m` (`CARDDEMO_SESSION_TIMEOUT`). |
| 3.4.1 | Cookie `Secure` | ✅ | 5C | `true` in `aws` e `tls`; `AwsProfileIT.sessionCookieHardened`. |
| 3.4.2 | Cookie `HttpOnly` | ✅ | 5C | `SecurityHardeningIT.sessionCookieIsHttpOnlySameSiteStrict`. |
| 3.4.3 | `SameSite` | ✅ | 5C | `Strict` (ADR-002). |
| 3.4.4 | Prefisso `__Host-` | ✅ | 5C | `__Host-CARDDEMO_SESSION` in `aws` (Path=/, senza Domain). |
| 3.5.x | Token stateless (JWT) | n/a | X3 | Solo per client macchina delle estensioni (ADR-002); da definire con X3. |

## V4 — Controllo accessi (autorizzazione oggetto per oggetto)

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 4.1.1 | Autorizzazione lato server | 🟡 | O1 | Oggi: catena Fase 0 `anyRequest().authenticated()`. O1 deve sostituirla con ruoli `ROLE_ADMIN`/`ROLE_USER` da `SEC-USR-TYPE`. |
| 4.1.2 | Attributi di sicurezza non manipolabili | ⏳ | O1 + online-common | `ConversationContext.userId/userType` in ingresso **ignorati**; usare solo il `SecurityContext` (vedi [cobol-authz-fixes.md](cobol-authz-fixes.md)). |
| 4.1.3 | Least privilege | ⏳ | O1, O2, O3, O4, O5, X2 | Admin-only: COADM01, COUSR00–03, COTRTLI/UP, COACTUP, COCRDUP, COTRN02, `/actuator/maintenance`. Test 403 per `USER0001` su ognuno. |
| 4.1.5 | Fail closed | ✅ | Fase 0 / 5C | Default deny (`anyRequest().authenticated()`); 401/403 con header di sicurezza (`SecurityHardeningIT`). |
| 4.2.1 | IDOR | ⏳ | O2, O3, O4, X1, X3 | Policy utente → cliente → `card_xref` → conto/carta/transazione su COACTVW, COCRDLI/SL, COTRN00/01, COBIL00, COPAU00/01, `/jobs/{id}`, MQ inquiry. Test negativi con `USER0002`. |
| 4.2.2 | CSRF | ⏳ | O1 + O6 | Catena Fase 0 ha CSRF disabilitato (HTTP Basic). Con sessione cookie O1 deve abilitare `CookieCsrfTokenRepository` per il frontend O6; `SameSite=Strict` è difesa in profondità. |
| 4.3.1 | Interfacce admin protette | 🟡 | 5A/5B | In `aws` actuator espone solo `health`; `/actuator/maintenance` va protetto con ruolo ADMIN. |

## V5 — Validazione, sanitizzazione, encoding

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 5.1.1–5.1.4 | Validazione input tipizzata, allow-list | ⏳ | O2–O5, X1–X3 | Bean Validation sui DTO di schermata secondo le PIC COBOL (lunghezze, numerici, date); `action` limitato all'enum AID del contratto. |
| 5.1.5 | Redirect sicuri | n/a | — | Nessun redirect parametrico. |
| 5.2.x | Sanitizzazione output | ✅/⏳ | 5C / O6 | Header CSP `default-src 'self'`, `frame-ancestors 'none'` (5C); O6 non deve usare `innerHTML` sui campi dei dati. |
| 5.3.4 | Query parametrizzate | ⏳ | tutti | Solo Spring Data / JPA / `JdbcTemplate` con parametri; nessuna concatenazione (CodeQL `java/sql-injection` in CI). |
| 5.3.8 | OS command injection | ✅ | 5C | CodeQL security-extended in CI. |
| 5.5.x | Deserializzazione | ✅/⏳ | 5C / X1, X3 | Jackson senza default typing (CodeQL); messaggi SQS validati contro schema (X1/X3). |

## V7 — Log ed errori

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 7.1.1 | Nessuna credenziale/token nei log | ✅ | 5C | `AccessAuditFilter` legge dal body solo `action`; `StructuredAuditEventRepository` scarta messaggi d'eccezione e session id; `SecurityHardeningIT` verifica assenza di password e numero conto. |
| 7.1.2 | Nessun dato sensibile (PAN/SSN) | ✅/⏳ | 5C / tutti | Audit 5C senza body; i workstream non devono loggare PAN/SSN (Checkstyle/review). |
| 7.1.3 | Eventi di sicurezza loggati | ✅ | 5C | `AUTHENTICATION_SUCCESS/FAILURE`, `AUTHORIZATION_FAILURE`, `LOGOUT`, `SESSION_ROTATED`, `ACCESS` (SIGNON/ADMIN/UPDATE/READ/JOB). |
| 7.1.4 | Metadati per indagine | ✅ | 5C | `principal`, `category`, `method`, `path`, `screen`, `action`, `status`, `outcome`, `remoteAddress` (reale dietro ALB), `requestId` (`X-Request-Id`), timestamp. |
| 7.2.1 / 7.2.2 | Decisioni di autenticazione e accesso | ✅ | 5C | Eventi Boot Actuator + `SpringAuthorizationEventPublisher`. |
| 7.3.1 | Encoding contro log injection | ✅ | 5C | `AuditLog` sostituisce CR/LF/controlli, lunghezza max 128 (`AuditLogTest`). |
| 7.3.3 / 7.3.4 | Protezione e sincronizzazione log | 🟡 | 5A | JSON su stdout → CloudWatch Logs (retention, KMS, accesso IAM ristretto) da configurare in 5A; timestamp UTC ISO-8601. |
| 7.4.1 | Errori generici | ✅ | 5C | `server.error.include-*=never`, whitelabel disabilitato. |

## V8 — Protezione dei dati

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 8.1.1 | No caching di dati sensibili | ✅ | 5C | `Cache-Control: no-store` su `/api/**` (`SecurityHeadersFilterTest`). |
| 8.2.x | Dati sensibili nel browser | ⏳ | O6 | Nessun PAN/SSN in localStorage. |
| 8.3.1 | Dati sensibili non in URL | ⚠️ | X3 / contratti | `GET /api/v1/ext/mqinquiry/accounts/{acctId}` espone l'id conto nel path (finisce in access log ALB). Accettabile (non PAN); se si decide diversamente: `CONTRACT-CHANGE`. |
| 8.3.4 | Masking PAN/SSN | ⏳ | O2, O3, O4, X1 | PAN `************1234`, SSN `***-**-1234` per utenti regolari (fix COBOL). |

## V9 — Comunicazioni

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 9.1.1 | TLS per tutto il traffico client | ✅/⏳ | 5C / 5A | Profilo `tls` (TLS 1.3/1.2) per TLS fino al task; ALB HTTPS-only con redirect 80→443 (5A). HSTS 1 anno + `includeSubDomains` (`AwsProfileIT`, `TlsProfileIT`). |
| 9.1.2 | Solo cipher forti | ✅ | 5C | Solo suite AEAD ECDHE/TLS1.3; `TlsProfileIT.rejectsNonAeadCipherSuites`. ALB: policy `ELBSecurityPolicy-TLS13-1-2-2021-06` (5A). |
| 9.2.2 | TLS verso backend | 🟡 | 5A | RDS `sslmode=verify-full` (documentato in `application-aws.yml`); endpoint AWS SDK HTTPS di default. |

## V10/V14 — Configurazione, dipendenze, header

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 10.3.2 / 14.2.1 | Componenti senza vulnerabilità note | ⚠️ | Fase 0 / 5A | Dependency-Check (CVSS ≥ 7) + Trivy in `modern-security.yml`. Baseline di CVE reali con scadenza 2026-11-30: [dependency-baseline.md](dependency-baseline.md). Richiede upgrade Spring Boot nel POM padre. |
| 14.1.x | Build ripetibile e sicura | ✅ | Fase 0 / 5C | Maven wrapper, immagine non-root, CodeQL, gitleaks, Trivy. |
| 14.3.2 | No debug in produzione | ✅ | 5C | Stack trace disattivati, actuator ridotto in `aws`. |
| 14.4.1–14.4.7 | Header di sicurezza | ✅ | 5C | `SecurityHeadersFilter`: CSP, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, COOP/CORP, HSTS su TLS. |
| 14.5.3 | CORS | ✅ | Fase 0 | Nessuna configurazione CORS ⇒ solo same-origin. O6 deve servire il frontend dalla stessa origine o configurare una allow-list esplicita. |

## V6 — Crittografia

| ID | Requisito | Stato | Owner | Implementazione / verifica |
|---|---|---|---|---|
| 6.1.1–6.1.3 | Dati regolamentati cifrati a riposo | ⏳ | 5A | RDS e S3 cifrati con KMS; CVV (`CARD-CVV-CD`) non dovrebbe essere conservato (PCI DSS 3.2): il dato esiste nel modello legacy → decisione di business da registrare. |
| 6.2.1 | Algoritmi approvati, fail sicuro | ✅ | 5C | BCrypt via `DelegatingPasswordEncoder` (O1); TLS AEAD; nessuna crittografia custom. |
| 6.4.1 | Gestione chiavi | 🟡 | 5A | Secrets Manager / KMS; rotazione secret RDS gestita da AWS. |
