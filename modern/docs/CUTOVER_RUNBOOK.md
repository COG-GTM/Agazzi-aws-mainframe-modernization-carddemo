# CardDemo — Runbook di cutover (COBOL/CICS/VSAM → Java 21 su AWS)

Fase 5D del [piano](../../docs/MIGRATION_PLAN_JAVA21.md) (§11.5 parallel run, "Fase 5"). Strumenti:
[`tools/cutover/`](../tools/cutover/README.md), IaC: [`deploy/terraform/`](../deploy/README.md),
rollback: [`docs/cutover/rollback.md`](cutover/rollback.md), decommissioning:
[`docs/cutover/decommissioning.md`](cutover/decommissioning.md).

Tutti i comandi si lanciano da `modern/` salvo diversa indicazione. Le credenziali arrivano solo da
variabili d'ambiente (`CARDDEMO_DB_PASSWORD`, `CARDDEMO_SMOKE_USER`, `CARDDEMO_SMOKE_PASSWORD`) lette da
Secrets Manager: `export CARDDEMO_DB_PASSWORD=$(aws secretsmanager get-secret-value --secret-id "$(terraform -chdir=deploy/terraform output -raw db_secret_arn)" --query SecretString --output text | jq -r .password)`.
Mai su riga di comando, mai nei report.

## 0. Ruoli (owner)

| Sigla | Ruolo | Responsabilità |
|---|---|---|
| **CM** | Cutover manager | conduce il runbook, decide go/no-go con BO |
| **BO** | Business owner CardDemo | firma go/no-go, comunicazioni agli utenti |
| **MFO** | Mainframe operations | CICS (CEMT), IDCAMS, JCL, Control-M/CA-7 |
| **PLT** | Platform/AWS (1C, 3A) | Terraform, ECS, RDS, ALB/DNS, S3/SQS |
| **DM** | Data migration (1B) | `vsamToRelational`, `relationalToVsam`, riconciliazione |
| **QA** | Parity/QA (Fase 0) | parallel run, smoke test, report |
| **SEC** | Security | segreti, accessi, chiusura utenze RACF |
| **SD** | Service desk | comunicazioni e ticket utenti |

## 1. Prerequisiti (entry criteria per T-14)

1. Fasi 1–4 mergiate su `migration/phase-5`; CI verde (`./mvnw -B verify`, `-Pparity,it verify`).
2. Job Spring Batch registrati con i nomi JCL (`POSTTRAN`, `INTCALC`, `COMBTRAN`, `TRANREPT`, `CREASTMT`, `TRANBKP`, …) e `vsamToRelational` / `relationalToVsam` di 1B con l'interfaccia di [rollback.md §2](cutover/rollback.md#2-contratto-cli-di-relationaltovsam-e-vsamtorelational).
3. Endpoint online di `openapi.yaml` (CC00 `cosgn00`, CAVW `coactvw`, CT02 `cotrn02`) e `/actuator/maintenance` (B5) esposti: `tools/cutover/smoke_test.py --strict` contro l'ambiente `dryrun` deve dare OK.
4. `PARITY_EXCEPTIONS.md` approvato dal BO (le sole differenze ammesse nel parallel run).
5. Ambienti AWS `dryrun`, `parallel`, `prod` creati con `deploy/terraform` (vedi [deploy/README.md](../deploy/README.md)).
6. `tools/cutover/tests/selftest.sh` verde sulla macchina di cutover (bastion con Java 21, Python 3 + PyYAML, AWS CLI, psql).

## 2. Timeline

| Finestra | Durata | Attività |
|---|---|---|
| T-14 | 1 g + 7 g freeze | freeze COBOL, dry-run migrazione completa, riconciliazione |
| T-7 → T-1 | 7 giorni lavorativi | parallel run notturno, report giornaliero, go/no-go giornaliero |
| T-0 | finestra notturna 6 h (22:00–04:00) | CLOSEFIL, migrazione incrementale, riconciliazione, switch, smoke test |
| T+0 → T+5 | 5 giorni | finestra di rollback (mainframe "warm", CICS chiuso ma pronto) |
| T+30 → T+90 | — | decommissioning a ondate |

## 3. T-14 — Freeze e dry-run

| # | Owner | Passo | Comando / azione | Tempo |
|---|---|---|---|---|
| 14.1 | CM | Annuncio freeze modifiche COBOL/JCL/CSD/copybook (vedi §8) | mail + ticket CAB | 15 min |
| 14.2 | MFO | Blocco promozioni su librerie `CARDDEMO.*` (Endevor/ChangeMan) | regole SCM | 30 min |
| 14.3 | QA | Rigenera golden e verifica harness | `./tools/golden/run-cobol.sh all --verify` | 10 min |
| 14.4 | MFO | Unload VSAM → PS (REPRO, RECFM=F) dei master | submit `app/jcl/REPTFILE.jcl`-style REPRO per ACCTDATA, CARDDATA, CARDXREF, CUSTDATA, TCATBALF, DISCGRP, TRANTYPE, TRANCATG, TRANSACT, USRSEC | 30 min |
| 14.5 | MFO | Trasferimento binario su S3 | `aws s3 cp --recursive ./unload/ s3://$GDG_BUCKET/cutover/T-14/` | 20 min |
| 14.6 | PLT | Ambiente `dryrun` pulito | `terraform -chdir=deploy/terraform apply -var-file=env/dryrun.tfvars` (pipeline PLT) | 40 min |
| 14.7 | DM | Migrazione completa | `CARDDEMO_JOB_MODE=ecs tools/cutover/run-job.sh vsamToRelational datasets=ALL format=ebcdic input=s3://$GDG_BUCKET/cutover/T-14/` | 30 min |
| 14.8 | DM | Riconciliazione completa | `tools/cutover/cutover.sh reconcile --db-url "$DB_URL" --source-root /data/T-14 --format ebcdic --out reports/T-14/reconciliation` | 10 min |
| 14.9 | QA | Smoke test read-only | `tools/cutover/smoke_test.py --base-url https://dryrun.carddemo.internal --strict --report reports/T-14/smoke.json` | 5 min |
| 14.10 | DM | Prova rollback completa (round-trip) | `tools/cutover/rollback/relational-to-vsam.sh --out /data/T-14-rollback --no-freeze-check --db-url "$DB_URL"` poi REPRO su HLQ di test | 1 h |
| 14.11 | CM | Misura tempi reali di 14.7–14.10 e aggiorna §5 | — | — |

**Go/no-go T-14 → T-7**: 14.8 `RECONCILIATION OK` su tutti gli 11 dataset; 14.9 OK; 14.10 round-trip con REPRO senza errori e riconciliazione OK; tempo totale 14.7+14.8 ≤ 50% della finestra T-0.

## 4. T-7 → T-1 — Parallel run (piano §11.5)

Il mainframe resta il sistema di record. Ogni notte, dopo il batch mainframe:

| # | Owner | Passo | Comando / azione | Tempo |
|---|---|---|---|---|
| 7.1 | MFO | Copia degli input del giorno (DALYTRAN, master pre-batch) su S3 | `aws s3 cp ... s3://$GDG_BUCKET/parallel/$D/input/` | 15 min |
| 7.2 | DM | Carica gli input nell'ambiente `parallel` | `CARDDEMO_JOB_MODE=ecs tools/cutover/run-job.sh vsamToRelational datasets=ALL format=ebcdic input=s3://$GDG_BUCKET/parallel/$D/input/` | 20 min |
| 7.3 | QA | Esegue la catena batch Java con la stessa data di business | `for j in POSTTRAN INTCALC COMBTRAN TRANREPT CREASTMT; do CARDDEMO_JOB_MODE=ecs tools/cutover/run-job.sh $j run.date=$D || break; done` | 30 min |
| 7.4 | MFO | Scarica gli output mainframe (`<JOB>/<DD>.dat` testo + `RETURN-CODES.txt`) | `aws s3 cp --recursive s3://$GDG_BUCKET/parallel/$D/mainframe/ /data/pr/$D/mainframe/` | 15 min |
| 7.5 | QA | Scarica gli output Java (stesso layout) | `aws s3 cp --recursive s3://$GDG_BUCKET/parallel/$D/java/ /data/pr/$D/java/` | 5 min |
| 7.6 | QA | Confronto + report giornaliero | `tools/cutover/cutover.sh compare --mainframe /data/pr/$D/mainframe --java /data/pr/$D/java --jobs POSTTRAN,INTCALC,COMBTRAN,TRANREPT,CREASTMT --date $D --out reports/parallel-run` (job mancanti o `RETURN-CODES.txt` assenti = NO-GO) | 5 min |
| 7.7 | QA | Riconciliazione master post-batch (DB Java vs unload mainframe post-batch) | `tools/cutover/cutover.sh reconcile --db-url "$DB_URL" --source-root /data/pr/$D/master-post --format ebcdic --out reports/parallel-run/$D/reconciliation` | 10 min |
| 7.8 | QA | Pubblica `reports/parallel-run/$D/parallel-run-report.md` sul canale #carddemo-cutover | — | 5 min |
| 7.9 | CM | Triage differenze: bug → fix su `migration/phase-5` + giorno ripetuto; deviazione accettata → voce in `PARITY_EXCEPTIONS.md` firmata BO | — | — |

**Go/no-go giornaliero**: report `GO (no unregistered differences)`, `RECONCILIATION OK`, return code identici.
**Go/no-go T-1 → T-0** (CM + BO): ultimi **5 giorni consecutivi** GO, incluso almeno un fine mese
(INTCALC/CREASTMT) — se il calendario non lo consente, eseguire INTCALC/CREASTMT su una data di fine mese simulata;
nessun fix aperto; prova rollback 14.10 OK; comunicazioni T-0 inviate.

## 5. T-0 — Switch (finestra 22:00–04:00)

| # | Ora | Owner | Passo | Comando / azione | Tempo |
|---|---|---|---|---|---|
| 0.1 | 22:00 | CM | Bridge aperto, conferma go finale | checklist §7 | 10 min |
| 0.2 | 22:10 | SD | Banner manutenzione, stop accessi utenti | — | 5 min |
| 0.3 | 22:15 | MFO | Ultimo batch giornaliero completato (Control-M `DAILY-*` ok) | console Control-M | — |
| 0.4 | 22:20 | MFO | **CLOSEFIL** CICS: chiusura file (ACCTDAT, CARDDAT, CCXREF, CUSTDAT, TRANSACT, USRSEC, …) | submit `app/jcl/CLOSEFIL.jcl` (o `CEMT SET FIL(*) CLO`) | 5 min |
| 0.5 | 22:25 | MFO | Hold di tutti i job CardDemo in Control-M/CA-7 | `ctmpsm -HOLD` sulle cartelle `DAILY-*`, `WEEKLY-*`, `MONTHLY-*`; CA-7 `HOLD,JOB=` | 10 min |
| 0.6 | 22:35 | MFO | Unload finale VSAM → PS + SHA-256 + upload S3 | REPRO come 14.4; `aws s3 cp ... s3://$GDG_BUCKET/cutover/T-0/` | 30 min |
| 0.7 | 23:05 | DM | Migrazione incrementale (delta rispetto all'ultimo carico parallel) | `CARDDEMO_JOB_MODE=ecs tools/cutover/run-job.sh vsamToRelational datasets=ALL format=ebcdic mode=replace input=s3://$GDG_BUCKET/cutover/T-0/` | 30 min |
| 0.8 | 23:35 | DM | **Riconciliazione** (gate) | `tools/cutover/cutover.sh reconcile --db-url "$DB_URL" --source-root /data/T-0 --format ebcdic --out reports/T-0/reconciliation` → deve stampare `RECONCILIATION OK` | 10 min |
| 0.9 | 23:45 | CM+BO | **Go/no-go switch** | firma su `reports/T-0/reconciliation/reconciliation.md` | 10 min |
| 0.10 | 23:55 | PLT | Switch DNS/API Gateway: ALB `prod` in servizio, pagina di manutenzione rimossa | `terraform -chdir=deploy/terraform apply -var-file=env/prod.tfvars -var maintenance_page=false`; Route 53: `carddemo.<dominio>` → `alb_dns_name` (TTL abbassato a 60 s a T-1) | 15 min |
| 0.11 | 00:10 | PLT | Apertura servizi Java (OPENFIL lato Java) | `POST /actuator/maintenance` (B5, ruolo ADMIN) con body `{"closedFiles":""}` oppure `aws ecs update-service --cluster carddemo-prod --service carddemo-prod-app --desired-count 2` | 5 min |
| 0.12 | 00:15 | QA | **Smoke test** con scrittura | `tools/cutover/smoke_test.py --base-url https://carddemo.<dominio> --strict --allow-write --posttran --report reports/T-0/smoke.json` (con `CARDDEMO_JOB_MODE=ecs`) | 10 min |
| 0.13 | 00:25 | QA | Storno della transazione di prova (TDESC `SMOKE TEST CUTOVER`, importo -1.00) e verifica saldo | CT02 con importo opposto, poi `smoke_test.py` read-only | 10 min |
| 0.14 | 00:35 | PLT | Abilita gli schedule EventBridge del batch Java (3A) | pipeline 3A | 10 min |
| 0.15 | 00:45 | CM+BO | **Go-live** dichiarato oppure rollback (§6) | — | 5 min |
| 0.16 | 00:50 | SD | Rimozione banner, comunicazione go-live | §8 | 10 min |

Margine: ~3 h nella finestra per un rollback completo prima delle 04:00.

**No-go a 0.8/0.9/0.12** → rollback "light" (§6, R.1–R.3a: il mainframe non ha ricevuto scritture, basta riaprire CICS).

## 6. Rollback (T-0 → T+5)

Dettagli e contratto CLI: [cutover/rollback.md](cutover/rollback.md).

| # | Owner | Passo | Comando | Tempo |
|---|---|---|---|---|
| R.1 | CM+BO | Decisione (criteri rollback.md §1) | — | 15 min |
| R.2 | PLT | Stop traffico Java: pagina manutenzione, CLOSEFIL lato Java | `terraform ... -var maintenance_page=true`; `POST /actuator/maintenance` (ruolo ADMIN) con body `{"closedFiles":"ACCTDAT,CARDAIX,CARDDAT,CCXREF,CUSTDAT,CXACAIX,TRANSACT,USRSEC"}` | 10 min |
| R.3a | MFO | *Prima di qualsiasi scrittura Java* (no-go T-0): riapre CICS sui VSAM invariati | submit `app/jcl/OPENFIL.jcl`, release Control-M | 10 min |
| R.3b | DM | *Dopo scritture Java*: export PostgreSQL → VSAM | `tools/cutover/rollback/relational-to-vsam.sh --out /data/rollback/$(date +%F) --base-url https://carddemo.<dominio>` | 30 min |
| R.4 | MFO | Upload binario dei file, submit `ROLLBACK.jcl` generato (DELETE/DEFINE/REPRO dei KSDS + rebuild AIX) | `sha256sum -c SHA256SUMS`, poi submit | 30 min |
| R.5 | MFO | OPENFIL CICS + release Control-M/CA-7 + DNS al mainframe/3270 | `app/jcl/OPENFIL.jcl` | 15 min |
| R.6 | QA | Verifica: riconciliazione VSAM (nuovo unload) vs DB congelato, transazioni CC00/CAVW/CT02 da 3270 | `cutover.sh reconcile --format ebcdic` | 20 min |

## 7. Checklist di verifica

T-0 prima dello switch (0.1):
- [ ] parallel run: 5 report GO consecutivi archiviati in `reports/parallel-run/`
- [ ] prova rollback T-14 OK, tempi misurati entro la finestra
- [ ] `terraform plan` di `prod` senza modifiche inattese; RDS backup automatico attivo + snapshot manuale `pre-cutover`
- [ ] TTL DNS a 60 s da almeno 24 h
- [ ] segreti in Secrets Manager ruotati e verificati (nessuna credenziale in report/log)
- [ ] persone del §0 presenti sul bridge; matrice §8 aggiornata

Dopo lo switch (0.15):
- [ ] `reports/T-0/reconciliation/reconciliation.md` OK e firmato
- [ ] `reports/T-0/smoke.json` `"ok": true` in modalità `--strict`
- [ ] transazione di prova stornata
- [ ] dashboard CloudWatch: 5xx ALB = 0, latenza p95 < 500 ms, nessun messaggio in DLQ SQS
- [ ] job Control-M/CA-7 CardDemo in HOLD, schedule EventBridge abilitati

Giornaliera T+1…T+5: primo batch notturno Java completato con RC 0; report `TRANREPT` consegnato; nessun incidente Sev1/2.

## 8. Matrice di comunicazione

| Quando | Da | A | Canale | Contenuto |
|---|---|---|---|---|
| T-14 | CM | sviluppo COBOL, MFO, CAB | mail + ticket CAB | inizio freeze, data T-0 |
| T-7…T-1 ogni mattina | QA | CM, BO, team Fase 1–4 | #carddemo-cutover | `parallel-run-report.md` del giorno |
| T-1 | CM | BO, SD, utenti | mail | conferma finestra T-0, indisponibilità 22:00–04:00 |
| T-0 ogni passo gate (0.8, 0.9, 0.12, 0.15) | CM | bridge, BO | bridge + #carddemo-cutover | esito e decisione |
| T-0 go-live / rollback | BO | utenti, SD | mail + banner | sistema disponibile / ripristino su mainframe |
| Incidente T+0…T+5 | SD | CM (reperibile), PLT | PagerDuty `carddemo-prod` | Sev, impatto, ETA |
| T+5 | CM | BO, MFO | mail | chiusura finestra rollback, avvio decommissioning |

## 9. Uscita

Al termine di T+5 senza rollback: CM chiude la finestra, MFO mantiene CICS chiuso e i VSAM in sola lettura
fino all'avvio del [decommissioning](cutover/decommissioning.md).
