# Threat model (sintetico) — CardDemo Java 21

Scope: `modern/` in AWS (ALB → ECS Fargate `carddemo-app` → RDS PostgreSQL 16, SQS, S3 per i report,
Secrets Manager), più il profilo `local` (docker-compose). Metodo: STRIDE sui data flow, collegato
alla checklist [ASVS-L2.md](ASVS-L2.md) e alle correzioni COBOL in [cobol-authz-fixes.md](cobol-authz-fixes.md).

## Asset

| Asset | Dove | Classificazione |
|---|---|---|
| PAN (`CARD-NUM`), CVV (`CARD-CVV-CD`), scadenza | `card`, `card_xref`, `daily_transaction`, `transaction` | PCI DSS — riservato |
| SSN, data di nascita, FICO, indirizzi (`CUST-*`) | `customer` | PII — riservato |
| Saldi e limiti conto (`ACCT-*`) | `account`, `tran_cat_balance` | Integrità critica |
| Credenziali utente (`SEC-USR-PWD`) e tipo (`SEC-USR-TYPE`) | `app_user` | Segreto / privilegio |
| Credenziali DB, chiavi TLS | Secrets Manager, env ECS | Segreto |
| Audit trail (`carddemo.audit`) | stdout → CloudWatch Logs | Integrità / non ripudio |
| Report e file batch (statement, export) | S3 / volume batch | Riservato |

## Confini di fiducia

1. **Internet → ALB**: TLS 1.2+ terminato sull'ALB (certificato ACM). Fonte delle richieste anonime.
2. **ALB → ECS task**: rete privata VPC; `X-Forwarded-*` attendibile solo da qui
   (`server.forward-headers-strategy=native`, proxy interni di default di Tomcat). TLS end-to-end opzionale con il profilo `tls`.
3. **App → RDS / SQS / S3 / Secrets Manager**: ruolo IAM del task, `sslmode=verify-full` su RDS.
4. **Client MQ / estensioni → `/api/v1/ext/**`**: chiamanti macchina (futuro JWT, ADR-002).
5. **Batch (Spring Batch, nomi job = nomi JCL)**: avviati da `/api/v1/jobs` (CORPT00 → TRANREPT) o dallo scheduler, stesso DB.

## Minacce principali (STRIDE)

| # | STRIDE | Minaccia | Origine legacy | Mitigazione Java | Stato | Owner |
|---|---|---|---|---|---|---|
| T1 | S | Credential stuffing / brute force su COSGN00 | COBOL non ha lockout | Rate limit + lockout progressivo, messaggio generico, audit `AUTHENTICATION_FAILURE` | Audit ✅ (5C); lockout ⏳ | O1 + 5C |
| T2 | S | Password di default `PASSWORD` del seed V2 | `devin/*-usrsec-seed-creds` | Seed solo nei profili `local`/`test`; profilo `aws` senza seed (dati da 1B); rifiuto credenziali di default in prod | Parziale (seed escluso da `aws`) | O1 + 1B |
| T3 | E | Utente regolare raggiunge funzioni admin (COUSR0x, COADM01, COTRTUP, COACTUP, COCRDUP, COTRN02) fidandosi di `CDEMO-USRTYP-USER` nella commarea | `devin/*-admin-authz` | Ruolo dal `SecurityContext` lato server (mai dal `ConversationContext` del client), `@PreAuthorize("hasRole('ADMIN')")`, test 403 | ⏳ | O1, O2, O3, O4, O5, X2 |
| T4 | I/T | IDOR: lettura/modifica di conti, carte, transazioni, pagamenti di altri clienti cambiando `acctId`/`cardNum`/`tranId` | `devin/*-idor`, `*-acctview-*`, `*-billpay-*` | Policy di ownership (utente → customer → xref → account/card) applicata nel service, test negativi per ogni endpoint | ⏳ | O2, O3, O4, X1, ext-mqinquiry |
| T5 | I | Esposizione PAN/SSN completi in risposte e log | `COTRN01C`, `COACTVWC` fix | Mascheramento per utenti regolari; audit log senza body (`AccessAuditFilter` legge solo `action`) | Log ✅ (5C); masking ⏳ | 5C + O2/O3/O4 |
| T6 | T | Session fixation / hijacking, CSRF sulle POST di schermata | — | Sessione server-side (ADR-002), cookie `__Host-CARDDEMO_SESSION` `Secure; HttpOnly; SameSite=Strict`, rotazione id al login, CSRF token per il frontend | Cookie ✅ (5C); CSRF/rotazione ⏳ | O1 |
| T7 | T | Manipolazione del `ConversationContext` (commarea) inviato dal client | COCOM01Y è in memoria CICS, qui transita sul client | Campi di sicurezza (`userId`, `userType`) ignorati in input e ricavati dalla sessione | ⏳ | O1 + `carddemo-online-common` |
| T8 | R | Operazioni admin/aggiornamenti senza traccia | CICS SMF non migrato | Audit strutturato `carddemo.audit` (sign-on, deny, ACCESS per ADMIN/UPDATE/READ/JOB) JSON su CloudWatch, retention ≥ 1 anno | ✅ (5C) | 5C |
| T9 | T | Log injection / log forging via userid | — | `AuditLog` sanitizza CR/LF e caratteri di controllo, lunghezza limitata | ✅ (5C) | 5C |
| T10 | I | Segreti nel repo o nell'immagine | — | gitleaks su tutta la history, Trivy secret scanner, Secrets Manager / env ECS | ✅ (5C) | 5C |
| T11 | E/D | CVE nelle dipendenze (Tomcat, Spring, Netty, Jackson, pgjdbc) | — | Dependency-Check CVSS ≥ 7 + Trivy in CI; baseline a scadenza | ⚠️ baseline aperta, vedi [dependency-baseline.md](dependency-baseline.md) | Fase 0 / 5A |
| T12 | D | DoS su sign-on, export/report pesanti (`CORPT00`, `TRANREPT`), body JSON grandi | — | Limiti dimensione richiesta, timeout, job asincroni con 202 + polling `/api/v1/jobs/{id}`, WAF sull'ALB | ⏳ | 5A/5B + 4B |
| T13 | I | Errori verbosi (stack trace, messaggi SQL) | ABEND COBOL | `server.error.include-*=never`, whitelabel off, `ErrorResponse` del contratto | ✅ (5C) | 5C |
| T14 | S | Downgrade a HTTP / cookie in chiaro | — | HSTS 1 anno + includeSubDomains, cookie `Secure`, redirect 80→443 sull'ALB, TLS 1.2+ AEAD nel profilo `tls` | ✅ (5C, ALB ⏳ 5A) | 5C + 5A |
| T15 | E | Abuso di `/actuator/maintenance` o endpoint actuator | — | In `aws` solo `health` esposto; maintenance riservato ad ADMIN e auditato | Esposizione ✅; authz ⏳ | 5A/5B |
| T16 | T | Messaggi SQS falsificati (autorizzazioni X1, MQ inquiry) | MQ senza autenticazione | Policy SQS ristretta al ruolo del task, cifratura SSE, validazione schema messaggi | ⏳ | X1, ext-mqinquiry, 5A |

## Rischi residui accettati (da ratificare)

- Compatibilità password legacy in chiaro (PE-001) finché 1B non migra a BCrypt: solo profili `local`/`test`.
- Baseline CVE con scadenza 2026-11-30 (T11).
