# Fase 5A — Report di integrazione finale CardDemo → Java 21

Branch `migration/5a-integration` (da `origin/migration/phase-5`, merge di `origin/migration/phase-1-4`).
PR verso `migration/phase-5`; il merge spetta al leader. Nessun file sotto `app/` è stato modificato.

## 1. Cosa è stato integrato

| Area | Modifica | Finding |
|---|---|---|
| Merge | `migration/phase-1-4` (17 workstream + `contracts-v1.1`) dentro `migration/phase-5` (5B perf, 5C security, 5D cutover); conflitti risolti su `pom.xml`, `MIGRATION_PROGRESS.md`, `PARITY_EXCEPTIONS.md`, `ownership.yaml`, workflow CI | — |
| Reactor | modulo `security` nel reactor e dipendenza di `carddemo-app`; `security` dipende da `carddemo-contracts` solo in test (nessun ciclo con l'app) | — |
| Sicurezza | rimosso `Fase0SecurityAutoConfiguration`; catena O1 `OnlineAuthSecurityConfiguration` (Spring Security + Spring Session JDBC) attiva solo in servlet; **CSRF attivo** (cookie `XSRF-TOKEN` + header `X-XSRF-TOKEN`, `SameSite=Strict`; esenti solo le richieste con `Authorization`, cioè i client macchina HTTP Basic); hardening 5C (`SecurityHardeningAutoConfiguration`, audit) solo servlet; profili `local`, `it`, `aws` | — |
| Sessione | tutti i moduli online (O1–O5, X1, X2) leggono/scrivono `ConversationContext.SESSION_ATTRIBUTE`; O1 salva l'oggetto (non più JSON sotto `carddemo.conversationContext`) | 5B F6 |
| Serializzazione | `ConversationContext` (v1.1) e `CoactvwcService.State` (O2) sono `Serializable`: gli attributi di sessione passano dalla serializzazione di Spring Session JDBC | F1 |
| CCLI | `CardBrowser.readNext/readPrev(key, max, acctId)` con indice `card_acct_id` (equivalente CARDAIX); `CardListService` usa il path per account per utenti non admin e con filtro account | F2 |
| POSTTRAN | `PosttranStep15` con `ChunkTransaction`: flush + commit + clear ogni `CHUNK_SIZE` = 1.000 record DALYTRAN | F3 |
| INTCALC | `IntcalcTasklet.chunkTransactions`: flush + commit ogni `CHUNK_ACCOUNTS` = 500 account | F3 |
| Job non-web | `carddemo-app` parte con `spring.main.web-application-type=none`; `run-job.sh` e `run-batch-perf.sh` usano il JVM non-web; `run-job.sh` mappa `COMPLETED` → exit 0 anche se il JVM esce con l'RC dello step (RC 4 CBTRN02C = scarti, warning JCL) | F4 |
| O4 | `Corpt00Body`: `reportExecutionId` nel body JSON di `Corpt00Response` (oltre a header e `Location`) | O4 |
| O6 | `gen-bms-layouts.mjs` legge `x-runtime-input` dall'OpenAPI; `CRDSEL1..7`, `ACCTSID`, `TRTSEL1..7` sono editabili nella SPA | O6 |
| Porte v1.1 | `SqsMessageChannel.receiveUnacknowledged/acknowledge` (delete SQS solo all'ack); X3 `MqInquiryListener` fa ack dopo aver scritto reply/errore (redelivery se il processo cade); X2 `TrantypeAdminMenu implements AdminMenuContributor` (rimosso l'adapter locale `AdminMenuOption`) | v1.1 |
| Env var | i nomi di `modern/deploy` `local.container_env` (`CARDDEMO_DB_*`, `CARDDEMO_MESSAGING_TYPE`, `CARDDEMO_GDG_TYPE`, `CARDDEMO_GDG_BUCKET`, `CARDDEMO_REPORT_QUEUE_URL`, `AWS_REGION`) sono quelli letti da `application*.yml` e `docker-compose.yml`; `CARDDEMO_SQS_ENDPOINT` resta solo per LocalStack; `CARDDEMO_JOB_MODE` è letto da `run-job.sh` | 5D |
| Cutover selftest | il check 7 ora richiede `run-job.sh POSTTRAN` = 0 (job integrato); Flyway con baseline 2 sopra V1+V2 caricati a mano | F4 |
| Parità | `PARITY_EXCEPTIONS.md`: PE-001 `approved`; PE-002..PE-014 di 5C registrate (vedi §3) | — |

Porte v1.1 **non** adottate, con motivo:

- **O1 CA00 da `AdminMenuContributor`.** COADM02Y elenca sempre le opzioni 5 e 6. Con X2 spenta il COBOL mostra "This option is not installed ..." (scenario golden `CA00-004`). Se O1 costruisse il menu dai bean, le opzioni sparirebbero e la parità si romperebbe. Il menu resta statico; il bean X2 implementa la porta per un uso futuro.
- **B4 `GenerationStore.define`.** B4 tiene il catalogo GDG in DB (`GdgCatalog`, `dl_gdg_base`, IDCAMS RC 12). `FileSystemGenerationStore` non ha un catalogo, quindi spostare `define` sulla porta non è banale: va fatto insieme all'adapter S3 (`CARDDEMO_GDG_TYPE=s3`).

### Finding 5D EBCDIC — verificato

Ho decodificato `app/data/EBCDIC/*.PS` in CP037 e li ho confrontati record per record con `app/data/ASCII/*.txt` (da cui viene il seed V2):

| Dataset | Record | Diversi | Dettaglio |
|---|---:|---:|---|
| ACCTDATA | 50 | 1 | account `00000000049`, offset 102: EBCDIC `ZEROAPR`, ASCII `A000000000` |
| DISCGRP | 51 | 1 | `DEFAULT` / `07` / `0001`, `DIS-INT-RATE`: EBCDIC `15.00`, ASCII `0.00` |
| CARDXREF, CUSTDATA | 50, 50 | 0 | — |

L'offset 102 cade in `ACCT-ADDR-ZIP` per CVACT01Y. `docs/cutover/reconciliation.md` (5D) dice "record 50" contando da 1, cioè lo stesso account 49. Il resto (`reconcile EBCDIC (CP037): 8 datasets MATCH` nel selftest) coincide. Il finding non è un difetto dei tool. I golden e il seed usano i campioni **ASCII**. Al T-14 la sorgente sarà l'unload reale, quindi questi due campioni non vanno usati come riferimento di riconciliazione. Il punto resta aperto per 1B: va scelto quale campione è canonico.

## 2. Risultati di parità per livello

Strategia a 5 livelli del piano (§ Fase 5). Tutti i comandi sono stati eseguiti su questa branch.

| Livello | Cosa | Comando | Esito |
|---|---|---|---|
| L1 record/codec | copybook, utility Assembler/date, decoder | `./mvnw -B verify` | BUILD SUCCESS, 23 moduli, 0 failure |
| L2 batch vs golden GnuCOBOL | POSTTRAN, INTCALC, COMBTRAN, TRANREPT, CREASTMT, READACCT | `tools/golden/run-cobol.sh all --verify` | 0 diff, exit 0 |
| L2 comparatori `carddemo-parity` | golden B1–B5 (incluso `fullBatchCycle` B5 sul seed) | `./mvnw -B -Pparity verify` | 1.161 test, 0 failure/error |
| L3 snapshot DB | snapshot relazionali dopo i job (B1–B5) e round-trip VSAM ↔ relazionale | `-Pparity` + round-trip §6 | MATCH |
| L4 scenari online | 38 scenari YAML CC00..CB00 (O1 38, O2 63, O3 39, O4 92, O5 34 step); nessuno più `pending` | `./mvnw -B -Pparity,it install` | 0 failure; il runner tiene sessione e CSRF per scenario |
| L5 flusso misto ed estensioni | CT02 → POSTTRAN → CAVW sull'app integrata; X1 ON/OFF (5+5), X2 (30+1), X3 (2) | smoke §6, verifica manuale, `-Pparity` | OK |

Le differenze trovate sono state corrette (sessione, CSRF nei runner, job non-web) oppure registrate come PE in `PARITY_EXCEPTIONS.md`.

## 3. Eccezioni di parità

- **PE-001** (BCrypt al posto della password in chiaro di USRSEC): `approved`.
- **PE-002..PE-014** (5C, PR #53) registrate. `approved`: PE-002..PE-006, PE-009, PE-012..PE-014.
- **Dubbie, restano `proposed`** (cambiano una funzione visibile, decide il business owner): PE-007 (CT02 solo admin), PE-008 (PAN mascherato in CT01), PE-010 e PE-011 (CCUP/CAUP solo admin).
- **Nota F3.** POSTTRAN e INTCALC ora fanno commit ogni chunk. Un abend a metà lascia i chunk già committati, come il REWRITE VSAM del COBOL (nessun rollback). La PE proposta in #41 ("INTCALC rollback atomico") non serve più: il comportamento è tornato quello COBOL. Il restart resta quello JCL: si riparte da backup (TRANBKP/`run-job.sh` + snapshot).

## 4. Stato sicurezza

- **Catena attiva.** È attiva solo `OnlineAuthSecurityConfiguration` (O1), in ambiente servlet; il fallback Fase 0 è stato rimosso. L'autenticazione passa per `CredentialAuthenticationProvider` su `app_user` con hash BCrypt (PE-001). La sessione è server-side in Spring Session JDBC: cookie `CARDDEMO_SESSION` `HttpOnly`, `SameSite=Strict` e `Secure` nel profilo `aws`. Le schermate admin richiedono `ROLE_ADMIN`.
- **CSRF.** È attivo con double-submit: cookie `XSRF-TOKEN` letto dalla SPA (`frontend/src/api`) e rimandato nell'header `X-XSRF-TOKEN`. Sono esenti solo le richieste con header `Authorization`, cioè i client macchina con HTTP Basic: smoke test, k6, runner di parità. Queste richieste non usano cookie ambientali, quindi non sono esposte al CSRF. Lo verificano `SecurityHardeningIT` (POST senza token → 403, con token → 200 e audit `ACCESS`) e `OnlineAuthSessionIT`.
- **Hardening 5C.** `SecurityHardeningAutoConfiguration` aggiunge header (CSP, HSTS dietro TLS, `nosniff`, `X-Frame-Options`, `no-store`), audit strutturato (`AUTHENTICATION_*`, `AUTHORIZATION_FAILURE`, `ACCESS` con categoria e senza PAN/SSN/password) e `X-Request-Id`. È tutto verificato end-to-end sull'app integrata (`SecurityHardeningIT`, `AwsProfileIT`, `TlsProfileIT`).
- **Profili.** `local` (docker compose, seed demo), `it` (Testcontainers) e `aws` (TLS dietro ALB, forward headers, credenziali di default rifiutate, nessun seed: PE-006).
- **Eccezioni di parità.** PE-001 (BCrypt) e PE-002..PE-014 di 5C sono registrate in `PARITY_EXCEPTIONS.md`. Sono `approved` quelle coerenti con il piano (§3.1, ASVS L2): PE-001..PE-006, PE-009, PE-012, PE-013, PE-014. Restano `proposed` e **dubbie** quelle che cambiano una funzione di business visibile, e vanno decise con il business owner:
  - PE-007 (CT02 solo admin: toglie un'opzione del menu utente COMEN01);
  - PE-008 (PAN mascherato in CT01);
  - PE-010 e PE-011 (CCUP/CAUP solo admin).

- **Debito CVE.** La baseline delle dipendenze (`security/dependency-check-suppressions.xml`, report 5C) scade il **2026-11-30**. Prima di quella data bisogna aggiornare Spring Boot all'ultima patch 3.x supportata e rieseguire `modern-security`.

<!--PERF-->

## 5. Prestazioni (×1.000) — misurate

Dettaglio e comandi in [`docs/perf/baseline.md`](perf/baseline.md) §3.1 e §4.1. Stessa macchina di
sviluppo (8 vCPU, app e Postgres in container, k6 locale): numeri indicativi, non di prod.

**Online, `online-mix.js` 200 VU × 5 min:** 263.326 richieste (763 req/s), 0,00 % errori HTTP,
100 % checks (nessun rimbalzo a COSGN00). p95 totale 351,4 ms, p90 171,8 ms. CCLI: p95 da 13.900 ms
(prima di F2) a **301,7 ms**. Il target p95 < 200 ms non è raggiunto: tutte le TX hanno p95
300–400 ms, segno di saturazione della macchina condivisa più che di una TX lenta.

**Batch, `run-batch-perf.sh`, `storage=db`, app `web-application-type=none`:**

| Job | Esito | Tempo | rec/s |
|---|---|---:|---:|
| POSTTRAN (DALYTRAN 300.000) | OK, RC 4 | 342,0 s | 877,1 |
| INTCALC (TCATBALF 50.000) | OK, RC 0 | 37,3 s | 1.341,3 |
| TRANREPT (TRANSACT 300.000) | OK, RC 0 | 13,4 s | 22.348,0 |
| CREASTMT (TRANSACT 300.000) | **abend** `WS-CARD-TBL overflow (> 51 cards)` | 14,3 s | — |
| fullBatchCycle senza CREASTMT | OK, RC 0 | 646,2 s | 464,3 |

Prima di F3/F4 POSTTRAN andava in timeout a 900 s e il contesto `none` non partiva. CREASTMT riproduce
il limite `OCCURS 51` / `OCCURS 10` di `CBSTM03A`: il dataset ×1.000 lo supera, il COBOL farebbe lo
stesso. Allargare le tabelle sarebbe una nuova PE, da decidere col business owner.

## 6. Cutover tooling (5D) sull'app integrata

| Verifica | Comando | Esito |
|---|---|---|
| Smoke test strict con scrittura e POSTTRAN | `CARDDEMO_SMOKE_USER=ADMIN001 CARDDEMO_SMOKE_PASSWORD=PASSWORD python3 tools/cutover/smoke_test.py --base-url http://localhost:8080 --strict --allow-write --posttran` | **GO**: health, maintenance (`closedFiles=[]`), signon, CAVW, CT02, POSTTRAN `COMPLETED` |
| Selftest cutover | `tools/cutover/tests/selftest.sh` | `SELFTEST OK (15 checks)` |
| Round-trip seed | `vsamToRelational` (`source=ascii`, `mode=replace`, `verify=true`) su un DB vuoto, poi `relationalToVsam` (`target=ascii`, `compare=../app/data/ASCII`) | entrambi `COMPLETED` (rc 0): 9 dataset, 626 record, export **byte-identico** al seed (`cmp`) |
| Terraform | `terraform -chdir=deploy/terraform init -backend=false && terraform -chdir=deploy/terraform validate` | `Success! The configuration is valid.` (nessun `plan`/`apply`) |

Correzioni fatte per arrivare al GO:

- **`/actuator/maintenance` (B5).** L'endpoint del runbook (T-0 0.11, R.2) non esisteva: lo smoke dava `PENDING`. Ora `MaintenanceEndpoint` (in `carddemo-app`) espone `MaintenanceModePort`:
  - `GET` per ogni utente autenticato;
  - `POST {"closedFiles":"ACCTDAT,CARDDAT"}` solo per ADMIN (CLOSEFIL); con `""` riapre tutto (OPENFIL).
  
  Il body è una stringa separata da virgole perché actuator lega il body come stringhe. Il runbook è stato aggiornato di conseguenza.
- **`smoke_test.py`.** Il test ora tiene un cookie jar per tutto il run, cioè una sessione terminale come la SPA e k6. Fa anche la prima SEND (`GET`) di CAVW e CT02 prima dell'input. Prima ogni chiamata partiva senza sessione: dopo il sign-on CAVW e CT02 non vedevano il COMMAREA.
- **`run-job.sh`.** Nel modo `local` il wrapper usa il profilo `local` di default. Così il JVM del job ha le stesse location Flyway (seed V2) dell'app compose. Senza, si fermava con `Detected applied migration not resolved locally: 2`.
- **POSTTRAN rieseguito sullo stesso DALYTRAN.** Una seconda esecuzione va in `ABEND-U0999`: `duplicate key ... in TRANSACT`. È lo stesso comportamento di CBTRN02C (WRITE TRANSACT con status 22 → abend 999), quindi non è una differenza. Prima di rilanciare lo smoke `--posttran` sullo stesso ambiente bisogna ripristinare i dati (`docker compose down -v`, oppure TRANBKP/restore).

## 7. Avvio locale per il test UI del leader

```bash
cd modern
docker compose up -d --build          # postgres 16 + localstack (SQS) + app su :8080 (profilo local, seed V2)
curl -s localhost:8080/actuator/health # {"status":"UP",...}
cd frontend && npm ci && CARDDEMO_API=http://localhost:8080 npm run dev   # SPA su http://localhost:5173
```

Con Docker Hub in rate limit (429) si possono usare le immagini base da `mirror.gcr.io`, ritaggate in locale:

```bash
docker pull mirror.gcr.io/library/eclipse-temurin:21-jre
docker tag mirror.gcr.io/library/eclipse-temurin:21-jre eclipse-temurin:21-jre
```

Stesso procedimento per `21-jdk`. Per Maven c'è `MAVEN_MIRROR_URL=...` come build arg.

Utenti demo del seed V2 (solo profilo `local`/`it`; nel profilo `aws` sono rifiutati, PE-006). Le password sono quelle pubbliche del campione CardDemo, non segreti:

| Utente | Password | Tipo |
|---|---|---|
| `ADMIN001`..`ADMIN005` | `PASSWORD` | admin (COADM01) |
| `USER0001`..`USER0005` | `PASSWORD` | utente (COMEN01) |

Il seed contiene i 50 account e le 50 carte del campione ASCII, ad esempio account `00000000001`. Per ripartire dal seed pulito: `docker compose down -v && docker compose up -d`.

## 8. Rischi residui

- **p95 online < 200 ms non raggiunto** (§5): 351 ms totale, CCLI 302 ms a 200 VU, misurato su una sola macchina condivisa da k6, app e Postgres. Va rimisurato su un ambiente dimensionato prima di dichiarare il target.
- **CREASTMT ×1.000** (§5): abend sul limite `OCCURS 51` del COBOL. Serve una decisione: dati reali entro il limite, oppure PE per allargare le tabelle.
- **INTCALC.** I record restano caricati in memoria per tutto il job. Il commit per chunk limita il lavoro perso, non la memoria.
- **Restart a metà job.** Dopo un abend, POSTTRAN/INTCALC lasciano committati i chunk già fatti, come il COBOL. Il restart va da backup (TRANBKP/snapshot): non c'è restart da checkpoint.
- **Campioni EBCDIC ≠ ASCII** su ACCTDATA (account 49) e DISCGRP (`DEFAULT/07/0001`), §1. Al T-14 va fissata la sorgente canonica, cioè l'unload reale da z/OS.
- **PE dubbie** PE-007, PE-008, PE-010, PE-011: servono una decisione del business owner e una nota di rilascio.
- **Porte v1.1 non adottate**: CA00 statico, `GenerationStore.define` in B4 (§1). L'adapter S3 GDG e `CARDDEMO_REPORT_QUEUE_URL` sono configurati in `deploy/`, ma nel profilo `local` non hanno un consumer verificato end-to-end.
- **Docker Hub rate limit** in CI e in locale: va bene un mirror, ma per la pipeline serve un registry interno.

## 9. Prima di un cutover reale

1. Validare `ROLLBACK.jcl` (5D) su z/OS reale: IDCAMS REPRO, ordine DELETE/DEFINE, AIX BLDINDEX. Qui è stato verificato solo il wrapper Java (`relational-to-vsam.sh`, selftest).
2. Aggiornare Spring Boot (oggi 3.3.5) all'ultima patch 3.x supportata **prima del 2026-11-30**, scadenza delle soppressioni in `security/dependency-check-suppressions.xml`. Poi rieseguire `modern-security` e tutta la parità.
3. Rieseguire perf online e batch su un ambiente `dryrun` con dimensionamento prod (RDS, ECS task) e dati ×1000 da unload reale.
4. Decidere PE-007/008/010/011 e la sorgente canonica EBCDIC.
5. Fare almeno un ciclo `parallel` (runbook T-7) con riconciliazione giornaliera, e la prova generale di rollback.
6. Lanciare `terraform plan` sugli ambienti reali con le credenziali del team piattaforma (qui solo `validate`).
