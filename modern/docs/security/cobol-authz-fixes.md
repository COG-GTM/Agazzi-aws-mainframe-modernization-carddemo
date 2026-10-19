# Correzioni di sicurezza COBOL → requisiti Java

Fonte: i branch `devin/*-authz`, `*-idor`, `*-admin-authz`, `*-usrsec-seed-creds` del repo, analizzati con
`git log origin/main..<branch>` e `git diff origin/main...<branch>`. Nessuno è mergiato in `main`, quindi la
**baseline di parità** (golden GnuCOBOL) contiene ancora il comportamento vulnerabile. Il Java implementa
il comportamento corretto: ogni divergenza è una parity exception da registrare (proposte `PE-002…PE-013`
nella descrizione della PR; `modern/PARITY_EXCEPTIONS.md` non viene modificato da 5C).

Principio comune a tutti i fix: **il COBOL si fidava della commarea** (`CDEMO-USER-ID`, `CDEMO-USER-TYPE`
in `COCOM01Y`), che nel Java corrisponde al `ConversationContext` inviato dal client. Nel Java identità e
ruolo vengono solo dal `SecurityContext` della sessione server-side (ADR-002); ogni campo di sicurezza del
`ConversationContext` in ingresso va ignorato.

Il test di sicurezza indicato va scritto dal workstream owner come `@Tag("security")` (o nella suite
`OnlineScenarioRunner`) e deve fallire sul comportamento legacy.

| # | Branch (commit) | Programma / transazione | Vulnerabilità legacy (CWE) | Endpoint Java | Workstream | PE proposta |
|---|---|---|---|---|---|---|
| 1 | `devin/1786999716-cousr01c-admin-authz` (`fe2d053`), `devin/1787177904-cousr01c-admin-authz` (`6512fe6`) | COUSR01C / CU01 — aggiunta utente | Nessun controllo di privilegio: chiunque arrivi alla mappa crea utenti, anche con `SEC-USR-TYPE='A'` (CWE-862, CWE-269). Tipo utente non validato. | `POST /api/v1/screens/cousr01` | O5 useradmin | PE-002 |
| 2 | `devin/1787177947-cousr02c-admin-authz` (`c93b84f`) | COUSR02C / CU02 — modifica utente | Nessun controllo admin; il privilegio veniva letto dalla commarea; password azzerata se il campo è vuoto (CWE-862, CWE-639). | `POST /api/v1/screens/cousr02` | O5 | PE-003 |
| 3 | `devin/1787177930-cousr03c-delete-authz` (`1c3cae8`) | COUSR03C / CU03 — cancellazione utente | Cancellazione senza controllo admin; possibile cancellare l'ultimo amministratore (lock-out) (CWE-862, CWE-284). | `POST /api/v1/screens/cousr03` (+ `cousr00` lista) | O5 | PE-004 |
| 4 | `devin/1787177978-adm-menu-authz` (`5646874`) | COADM01C / CA00 — menu admin | Il menu admin veniva mostrato e le opzioni dispatchate basandosi solo su `CDEMO-USRTYP-ADMIN` in commarea (CWE-602, CWE-285). | `GET/POST /api/v1/screens/coadm01` | O1 auth | PE-005 |
| 5 | `devin/1787177905-usrsec-seed-creds` (`99598fb`) | COSGN00C / CC00 + `USRSEC` seed (`DUSRSECJ`, `USRSECPW`) | Credenziali di default utilizzabili (`ADMIN001/PASSWORD` …) nel seed spedito; password in chiaro (CWE-798, CWE-256, CWE-521). Il fix disabilita gli account seed, rifiuta le password note, carica le password fuori banda. | `POST /api/v1/screens/cosgn00` + seed V2 / datamigration 1B | O1 + 1B | PE-006 (estende PE-001) |
| 6 | `devin/1786999789-cotrn02c-admin-authz` (`2c948e2`) | COTRN02C / CT02 — aggiunta transazione | Inserimento transazioni (movimenta saldi via POSTTRAN) da parte di utenti regolari; tipo utente preso dalla commarea (CWE-862). | `POST /api/v1/screens/cotrn02` | O4 transaction | PE-007 |
| 7 | `devin/1786999750-cotrn01c-transaction-idor` (`903b73c`) | COTRN01C / CT01 — dettaglio transazione | IDOR: qualsiasi `TRAN-ID` visibile a qualsiasi utente; PAN completo in chiaro (CWE-639, CWE-359). Il fix ricontrolla l'utente su USRSEC, verifica transazione→carta→conto→cliente e maschera il PAN. | `POST /api/v1/screens/cotrn01` (+ `cotrn00` lista) | O4 | PE-008 |
| 8 | `devin/1786999783-cocrdslc-card-view-authz` (`cef4516`) | COCRDSLC / CCDL — dettaglio carta | IDOR su `CARD-NUM`/`ACCT-ID`: dettagli carta (CVV, scadenza, nome) di altri clienti (CWE-639). | `POST /api/v1/screens/cocrdsl` (+ `cocrdli` lista) | O3 card | PE-009 |
| 9 | `devin/1786999842-cocrdupc-card-update-authz` (`73f85e1`) | COCRDUPC / CCUP — aggiornamento carta | Aggiornamento carta (stato, scadenza, nome) senza controllo admin e senza ownership; utente non riverificato prima della REWRITE (CWE-862, CWE-639). | `POST /api/v1/screens/cocrdup` | O3 | PE-010 |
| 10 | `devin/1786999850-acctupd-authz` (`b89dc67`), `devin/1787177909-coactupc-authz` (`ce4f3a2`) | COACTUPC / CAUP — aggiornamento conto/cliente | Modifica di limiti, saldi, SSN, FICO senza controllo admin; autorizzazione non ricontrollata prima della REWRITE (CWE-862, CWE-367). | `POST /api/v1/screens/coactup` | O2 account | PE-011 |
| 11 | `devin/1786999949-acctview-object-authz` (`e7f6073`), `devin/1787177918-coactvwc-acctview-idor` (`571f9b0`, 2 commit) | COACTVWC / CAVW — visualizzazione conto | IDOR su `ACCT-ID`: conto e anagrafica (SSN completo, FICO, DOB) di qualsiasi cliente; il fix aggiunge l'entitlement cliente in `CSUSR01Y` e maschera l'SSN agli utenti regolari (CWE-639, CWE-359). | `POST /api/v1/screens/coactvw` | O2 | PE-012 |
| 12 | `devin/1786999791-billpay-ownership-authz` (`5a51224`), `devin/1787177910-cobil00c-billpay-idor` (`a26df87`) | COBIL00C / CB00 — pagamento saldo | IDOR: pagamento (azzeramento saldo + scrittura TRANSACT) su qualunque `ACCT-ID` (CWE-639, CWE-840). Admin non ristretti. | `POST /api/v1/screens/cobil00` | O4 | PE-013 |
| 13 | `devin/1787178115-mq-acct-inquiry-authz` (`cac5961`) | COACCT01 (MQ) / CDRA — inquiry conto via MQ | Nessuna identità del richiedente nel messaggio MQ: chiunque possa scrivere sulla coda legge qualunque conto (CWE-306, CWE-639). Il fix richiede userid nel messaggio e verifica l'entitlement. | `GET /api/v1/ext/mqinquiry/accounts/{acctId}` (+ coda SQS) | ext-mqinquiry | PE-014 |

> Gli hash sono il tip di ciascun branch (`git log -1 --format=%h`), ognuno a 1 commit da `main` salvo dove indicato al momento
> dell'analisi (2026-10-19). I branch duplicati (stesso programma, run diverse della stessa automazione)
> sono equivalenti nel comportamento; il Java deve seguire la versione più restrittiva.

## Test di sicurezza da aggiungere (per voce)

Utenti di riferimento del seed V2: `ADMIN001` (tipo `A`), `USER0001` (tipo `U`, cliente/conto propri),
`USER0002` (tipo `U`, altro cliente). "Conto altrui" = un `acctId` non collegato al cliente di `USER0001`
via `card_xref`.

1. **COUSR01** — `USER0001` POST `cousr01` (ENTER con dati validi) → 403, nessuna riga in `app_user`, audit
   `AUTHORIZATION_FAILURE`. `ADMIN001` con `userType='X'` → errore di validazione, nessuna insert.
   Anche con `context.userType='A'` forgiato nel body → 403.
2. **COUSR02** — `USER0001` POST `cousr02` → 403. `ADMIN001` aggiorna con password vuota → hash invariato.
   `userType` fuori da `A/U` → rifiutato.
3. **COUSR03** — `USER0001` POST `cousr03` (PF5) → 403. `ADMIN001` cancella l'unico admin rimasto → errore,
   riga ancora presente.
4. **COADM01** — `USER0001` GET/POST `coadm01` → 403 (anche con `context.userType='A'`); `ADMIN001` → 200.
5. **COSGN00** — profilo `aws`: `ADMIN001/PASSWORD` → 401 e audit `AUTHENTICATION_FAILURE`; nessuna
   migrazione Flyway `db/seed` applicata; password memorizzate solo `{bcrypt}`. Messaggio d'errore identico
   per utente inesistente e password errata.
6. **COTRN02** — `USER0001` POST `cotrn02` (PF5 conferma) → 403, nessuna riga in `transaction`/`daily_transaction`.
7. **COTRN01** — `USER0001` POST `cotrn01` con `tranId` di un conto altrui → 403/“not found”, nessun dato
   restituito; per i propri `tranId` il PAN è mascherato (`************1234`). `cotrn00` lista solo le
   transazioni proprie.
8. **COCRDSL** — `USER0001` POST `cocrdsl` con `cardNum`/`acctId` altrui → negato; `cocrdli` filtra per
   cliente; CVV mai nella risposta.
9. **COCRDUP** — `USER0001` POST `cocrdup` (PF5) → 403, `card` invariata; `ADMIN001` → OK con
   optimistic lock (`version`).
10. **COACTUP** — `USER0001` POST `coactup` → 403; la verifica avviene di nuovo nel service prima del save
    (nessun TOCTOU tra lettura e update), `account`/`customer` invariati.
11. **COACTVW** — `USER0001` POST `coactvw` con `acctId` altrui → negato; sul proprio conto SSN mascherato
    (`***-**-1234`); `ADMIN001` vede tutto.
12. **COBIL00** — `USER0001` POST `cobil00` (conferma `Y`) su conto altrui → negato, saldo invariato,
    nessuna transazione `02` scritta; sul proprio conto OK.
13. **MQ inquiry** — `GET /api/v1/ext/mqinquiry/accounts/{acctId}` senza identità → 401; `USER0001` su conto
    altrui → 403; messaggio SQS senza `userId` → risposta d'errore, nessun dato.

Trasversale (5C, già oggi): `SecurityHardeningIT` verifica che ogni richiesta negata produca un evento di
audit strutturato e che l'audit non contenga body/PAN/password; `AuditCategoryTest` fallisce se un nuovo
path di `openapi.yaml` non ha categoria di audit.
