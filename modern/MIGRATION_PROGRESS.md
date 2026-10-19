# MIGRATION_PROGRESS — CardDemo → Java 21

Aggiornata dal leader a ogni merge (piano §4.5). Ordine di merge suggerito: 1C → 1A → 1B → B4 → B1 → B2 → B3 → B5 → O1 → O2…O5 → O6 → X1–X3.

| WS | Titolo | Modulo | Flyway | Branch | Stato | PR | Test (unit/parity/IT) | Gate parità | CONTRACT-CHANGE | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `1a-copybook` | Copybook → record Java (codec completo) | `carddemo-copybook` | — | `migration/1a-copybook` | merged | [#43](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/43) | 71/5/0 | ✅ | — | |
| `1b-datamigration` | Schema relazionale e migrazione dati | `carddemo-datamigration` | V100–V199 | `migration/1b-datamigration` | merged | [#40](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/40) | 14/5/6 | ✅ | — | LegacyUserCredentialService: uppercase prima di BCrypt; encoded solo `{id}`+len>8 |
| `1c-platform` | Utility Assembler e date → Java | `carddemo-platform` | — | `migration/1c-platform` | merged | [#35](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/35) | 145/6/0 | ✅ | — | |
| `b1-posting` | Posting giornaliero transazioni | `carddemo-batch-posting` | V200–V299 | `migration/b1-posting` | merged | [#45](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/45) | 40/12/1 | ✅ | `GenerationStore.define` → v1.1; `carddemo-parity-core` non applicato | HARNESS-CHANGE `-fsign=EBCDIC` aperto; PE-B1-1..3 proposte |
| `b2-interest` | Interessi e saldi per categoria | `carddemo-batch-interest` | V300–V399 | `migration/b2-interest` | merged | [#41](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/41) | 34/9/1 | ✅ | — | PE proposta: INTCALC rollback atomico vs rewrite VSAM parziale |
| `b3-reports` | Report e statement | `carddemo-batch-reports` | V400–V499 | `migration/b3-reports` | merged | [#37](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/37) | 34/6/0 | ✅ | — | escaping HTML STMTHTML in attesa di PE; scenario DALYTRAN escluso (overpunch) |
| `b4-datalifecycle` | Data lifecycle: caricamento, export/import, utility | `carddemo-batch-datalifecycle` | V500–V599 | `migration/b4-datalifecycle` | merged | [#47](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/47) | 22/6/1 | ✅ | `GenerationStore.define` → v1.1 | adattatore locale GdgCatalog + V500; MaintenanceModePort durevole suggerito |
| `b5-orchestration` | Orchestrazione batch e scheduler | `carddemo-batch-orchestration` | V600–V699 | `migration/b5-orchestration` | merged | [#44](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/44) | 54/5/3 | ✅ | `sshd-sftp` in dependencyManagement → v1.1 | |
| `o1-auth` | Sign-on, menu, sicurezza | `carddemo-online-auth` | V1000–V1099 | `migration/o1-auth` | merged | [#38](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/38) | 61/41/41 | ✅ | Serializable, `SESSION_ATTRIBUTE`, `@JsonIgnore isAdmin` → v1.1 | PE-O1-1..5 proposte; chiave sessione locale da allineare |
| `o2-account` | Conto: visualizzazione e aggiornamento | `carddemo-online-account` | V1100–V1199 | `migration/o2-account` | merged | [#42](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/42) | 17/63/2 | ✅ | chiave sessione → v1.1; HTTP-session nel runner non applicato | |
| `o3-card` | Carte: lista, dettaglio, aggiornamento | `carddemo-online-card` | V1200–V1299 | `migration/o3-card` | merged | [#39](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/39) | 45/39/6 | ✅ | CRDSEL1..7, ACCTSID → v1.1 | PE proposta: REWRITE rifiuta carta spostata |
| `o4-transaction` | Transazioni, pagamenti, report | `carddemo-online-transaction` | V1300–V1399 | `migration/o4-transaction` | merged | [#46](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/46) | 75/92/3 | ✅ | `SESSION_ATTRIBUTE`, `reportExecutionId` → v1.1 | |
| `o5-useradmin` | Amministrazione utenti | `carddemo-online-useradmin` | V1400–V1499 | `migration/o5-useradmin` | merged | [#36](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/36) | 50/34/3 | ✅ | RECSTAMP non applicato (opzionale) | PE-O5-01..05 proposte |
| `o6-frontend` | Frontend SPA (opzionale) | `frontend` | — | `migration/o6-frontend` | merged | [#33](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/33) | 100/0/0 | ✅ | — | 45 contract test Prism |
| `x1-authorization` | Estensione autorizzazioni IMS/DB2/MQ | `carddemo-ext-authorization` | V2000–V2099 | `migration/x1-authorization` | merged | [#50](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/50) | 23/10/3 | ✅ | porta ownership + manifest non applicati (decisione leader) | CPVS/CPVD ownership e UNLDPADB atomicità aperti |
| `x2-trantype` | Estensione tipi transazione DB2 | `carddemo-ext-trantype` | V2100–V2199 | `migration/x2-trantype` | merged | [#48](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/48) | 39/31/32 | ✅ | TRTSEL1..7, `AdminMenuContributor` → v1.1; FK non applicata | role check admin aperto |
| `x3-mqinquiry` | Estensione inquiry via MQ | `carddemo-ext-mqinquiry` | V2200–V2299 | `migration/x3-mqinquiry` | merged | [#34](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/34) | 28/2/3 | ✅ | `receiveUnacknowledged`/`acknowledge` → v1.1 | |

## Integrazione Fasi 1–4

- Tutti i 17 workstream sono mergiati (squash) su `migration/phase-1-4`. I conteggi dei test vengono dal messaggio finale di ciascun workstream. #35–#48 e #34 erano già stati mergiati prima di questa integrazione. #50 (x1) è stato aggiornato su `phase-1-4` e verificato con `./mvnw -B -Pparity verify` verde e CI verde.
- `contracts-v1.1` ([#54](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/54), tag `contracts-v1.1`) contiene solo cambi additivi:
  - `ConversationContext` Serializable, `SESSION_ATTRIBUTE`, `@JsonIgnore isAdmin`;
  - `GenerationStore.define`;
  - `MessageChannelPort.receiveUnacknowledged`/`acknowledge`;
  - `AdminMenuContributor`;
  - `CRDSEL1..7`, `ACCTSID`, `TRTSEL1..7` (`x-runtime-input`);
  - `Corpt00Response.reportExecutionId`;
  - `sshd-sftp` in `dependencyManagement`.

## Fase 5A — integrazione finale (`migration/5a-integration` → `migration/phase-5`, [#57](https://github.com/COG-GTM/Agazzi-aws-mainframe-modernization-carddemo/pull/57))

Dettagli, comandi e numeri in `docs/PHASE5_REPORT.md`.

| Voce | Stato | Note |
|---|---|---|
| Merge `phase-1-4` su `phase-5` (5B #55, 5C #53, 5D #52) | ✅ | conflitti su `pom.xml`, progress, PE, ownership, workflow |
| `modern/security` nel reactor + O1 (Spring Security + Spring Session JDBC, CSRF) | ✅ | `Fase0SecurityAutoConfiguration` rimossa; profili `local`/`it`/`aws` |
| Chiave sessione unica `ConversationContext.SESSION_ATTRIBUTE` (F6) + stato Serializable (F1) | ✅ | O1–O5, `CoactvwcService.State` |
| F2 CCLI per account (indice `card_acct_id`, equivalente CARDAIX) | ✅ codice | p95 a 200 VU: vedi report §5 |
| F3 chunk POSTTRAN, INTCALC, COMBTRAN STEP10 | ✅ | commit ogni 1.000 record / 500 account |
| F4 `carddemo-app` non-web per i job batch | ✅ | `run-job.sh POSTTRAN` → COMPLETED |
| `fullBatchCycle` con TRANBKP prima di COMBTRAN | ✅ | come `scripts/run_full_batch.sh` |
| O4 `reportExecutionId` nel body, O6 `x-runtime-input` editabili | ✅ | |
| Porte v1.1: `AdminMenuContributor` (X2), ack `MessageChannelPort` (X3) | ✅ | CA00 statico e `GenerationStore.define` in B4: non adottati (report §1) |
| Env SQS/S3 allineati a `deploy/` (`local.container_env`) | ✅ | |
| Finding 5D EBCDIC ≠ ASCII (ACCTDATA, DISCGRP) | ✅ verificato | sorgente canonica da decidere (1B) |
| Parità L1–L5, cutover smoke/selftest/round-trip, `terraform validate` | ✅ | report §2, §6 |
| CREASTMT ×1000 | ⚠️ | abend voluto: CBSTM03A carica tutto TRNXFILE in `WS-CARD-TBL OCCURS 51` × `WS-TRAN-TBL OCCURS 10`; ×1000 ha 50.000 carte distinte. Limite del programma legacy, non della migrazione (report §5) |

## Follow-up aperti (non bloccanti, decisione leader / owner)

- ~~**Sessione online.**~~ risolto in 5A (#57). O1 salvava il contesto come JSON sotto `carddemo.conversationContext`, mentre O2–O5 lo leggono come oggetto sotto `ConversationContext.class.getName()`. Gli owner devono migrare entrambi su `ConversationContext.SESSION_ATTRIBUTE` (v1.1).
- **Adozione v1.1 nei moduli.** In 5A (#57) fatti O3/X2 `Input`, O4 `reportExecutionId`, X3 ack; restano CA00 da `AdminMenuContributor` (O1, statico per lo scenario `CA00-004`) e B4 `GenerationStore.define`.
  - O3 e X2 devono passare dagli adattatori locali ai nuovi campi `Input`.
  - O4 deve valorizzare `reportExecutionId` nel body.
  - O1 deve costruire CA00 dai bean `AdminMenuContributor`.
  - B4 deve usare `GenerationStore.define`.
  - X3 deve usare l'ack esplicito.
- ~~**O6 frontend.**~~ risolto in 5A (#57). I campi `x-runtime-input` (`CRDSEL*`, `TRTSEL*`, `ACCTSID`) non sono ancora editabili nella SPA, che usa gli attributi BMS statici.
- **Non applicati.** Questi cambi non sono additivi o richiedono una decisione:
  - `RECSTAMP` (O5);
  - HTTP-session in `OnlineScenarioRunner` (O2);
  - porta ownership user→account e manifest crash-atomico UNLDPADB (X1);
  - role check sugli endpoint admin (X2);
  - FK `transaction_category→transaction_type` (X2);
  - modulo `carddemo-parity-core` (B1);
  - HARNESS-CHANGE `-fsign=EBCDIC` in `tools/golden/golden.py` (B1): il golden Fase 0 POSTTRAN va rigenerato.
- **PARITY-EXCEPTION proposte nelle PR, da approvare.** Non sono ancora in `PARITY_EXCEPTIONS.md`: i comparatori leggono ogni riga, quindi aggiungerle allargherebbe il gate.
  - PE-B1-1..3 (#45);
  - INTCALC rollback atomico (#41);
  - escaping HTML STMTHTML (#37);
  - PE-O1-1..5 (#38);
  - COCRDUPC REWRITE (#39);
  - PE-O5-01..05 (#36);
  - DBPAUTP0 chiavi invalide e LOW-VALUES (#50).
