# PARITY_EXCEPTIONS — registro delle differenze volute COBOL ↔ Java (piano §11.6)

I comparatori di `carddemo-parity` (`ParityExceptions`, `RecordComparator`, `DbSnapshot`,
`OnlineScenarioRunner`) leggono **solo le righe di questa tabella** e ignorano esclusivamente le
differenze che coprono. Tutto il resto è un fallimento del gate di parità.

Formato colonne (non cambiarlo, il parser legge le prime tre):

- **ID** `PE-nnn` (progressivo, mai riusato)
- **Programma/Job**: nome del job golden (`POSTTRAN`, `INTCALC`, ...), programma (`COSGN00C`) o `*`
- **Campo**: nome COBOL del campo/colonna (`SEC-USR-PWD`, `ACCT-CURR-BAL`) o `*`
- **COBOL**, **Java**: comportamento osservato
- **Motivo**, **Approvatore**, **Stato** (`proposed` | `approved`)

Un workstream che introduce una differenza la aggiunge come `proposed` nella propria PR
(sezione `PARITY-EXCEPTION` della descrizione); il leader la porta ad `approved` al merge.

| ID | Programma/Job | Campo | COBOL | Java | Motivo | Approvatore | Stato |
|----|---------------|-------|-------|------|--------|-------------|-------|
| PE-001 | COSGN00C | SEC-USR-PWD | password in chiaro in USRSEC (PIC X(08)) | hash BCrypt in `app_user.password_hash` | sicurezza (piano §3.1, Spring Security 6) | leader migrazione (Fase 5A) | approved |
| PE-002 | COUSR01C | AUTHZ-USER-ADD | qualsiasi utente può aggiungere utenti, qualsiasi `SEC-USR-TYPE` | solo ADMIN, tipo ∈ {A,U} | sicurezza 5C (ASVS V4, #53) | leader migrazione (Fase 5A) | approved |
| PE-003 | COUSR02C | AUTHZ-USER-UPD | nessun controllo admin; password vuota sovrascrive | solo ADMIN; password vuota mantiene l'hash | sicurezza 5C (#53) | leader migrazione (Fase 5A) | approved |
| PE-004 | COUSR03C | AUTHZ-USER-DEL | nessun controllo admin; l'ultimo admin è cancellabile | solo ADMIN; ultimo admin protetto | sicurezza 5C (#53) | leader migrazione (Fase 5A) | approved |
| PE-005 | COADM01C | AUTHZ-ADMIN-MENU | si fida di `CDEMO-USRTYP-ADMIN` nella COMMAREA | ruolo dalla sessione server | sicurezza 5C (#53) | leader migrazione (Fase 5A) | approved |
| PE-006 | COSGN00C | AUTHZ-DEFAULT-CREDS | `ADMIN001/PASSWORD` utilizzabile | nel profilo `aws` password note/di default rifiutate, nessun seed, ≥12 caratteri per le nuove | sicurezza 5C (#53); in `local`/`it` il seed demo resta | leader migrazione (Fase 5A) | approved |
| PE-007 | COTRN02C | AUTHZ-TRAN-ADD | qualsiasi utente (opzione del menu utente COMEN01) | solo ADMIN | sicurezza 5C (#53) — **dubbia**: toglie una funzione di business agli utenti regolari | leader migrazione (Fase 5A) | proposed |
| PE-008 | COTRN01C | AUTHZ-TRAN-VIEW | qualsiasi `TRAN-ID`, PAN in chiaro | controllo ownership, PAN mascherato | sicurezza 5C (#53) — **dubbia** sul mascheramento: cambia l'output a video | leader migrazione (Fase 5A) | proposed |
| PE-009 | COCRDSLC | AUTHZ-CARD-VIEW | qualsiasi carta/conto | controllo ownership | sicurezza 5C (#53) | leader migrazione (Fase 5A) | approved |
| PE-010 | COCRDUPC | AUTHZ-CARD-UPD | qualsiasi utente, nessuna ownership | solo ADMIN + ricontrollo prima del REWRITE | sicurezza 5C (#53) — **dubbia**: nel COBOL è un'opzione del menu utente (ownership basterebbe) | leader migrazione (Fase 5A) | proposed |
| PE-011 | COACTUPC | AUTHZ-ACCT-UPD | qualsiasi utente | solo ADMIN + ricontrollo prima del REWRITE | sicurezza 5C (#53) — **dubbia**: come PE-010 | leader migrazione (Fase 5A) | proposed |
| PE-012 | COACTVWC | AUTHZ-ACCT-VIEW | qualsiasi conto, SSN in chiaro | controllo ownership, SSN mascherato per gli utenti | sicurezza 5C (#53) | leader migrazione (Fase 5A) | approved |
| PE-013 | COBIL00C | AUTHZ-BILL-PAY | qualsiasi `ACCT-ID` | controllo ownership (admin senza restrizioni) | sicurezza 5C (#53) | leader migrazione (Fase 5A) | approved |
| PE-014 | COACCT01 | AUTHZ-MQ-REQUESTER | nessuna identità del richiedente | richiedente autenticato + entitlement | sicurezza 5C (#53), estensione X3 | leader migrazione (Fase 5A) | approved |
| PE-015 | CREASTMT | WS-CARD-TBL | `OCCURS 51` carte in memoria; oltre il limite il programma scrive fuori tabella (comportamento indefinito) | tabella senza limite, indicizzata per carta | dataset di produzione (×1.000) oltre 51 carte | Simone Agazzi (business owner), 2026-10-19 | approved |
| PE-016 | CREASTMT | WS-TRAN-TBL | `OCCURS 10` transazioni per carta; oltre il limite scrive fuori tabella | nessun limite di transazioni per carta | come PE-015 | Simone Agazzi (business owner), 2026-10-19 | approved |

Note Fase 5A:

- PE-002..PE-014 vengono da 5C (#53). Sono differenze di **autorizzazione**: gli scenari golden
  girano con utenti autorizzati e non le osservano. Il campo `AUTHZ-*` è un identificatore che non
  corrisponde a nessun campo COBOL, così queste righe non allargano il gate dei comparatori.
- PE-007, PE-008, PE-010 e PE-011 restano `proposed` perché sono dubbie (vedi Motivo): serve una decisione
  del business owner prima del cutover. PE-001 e le altre sono `approved`.
- PE-015/PE-016: entro i limiti del COBOL (seed del repo, golden GnuCOBOL) l'output è identico. Oltre i
  limiti `CBSTM03A` non va in abend ma scrive oltre la tabella, quindi non c'è un comportamento COBOL
  da riprodurre. Il Java mantiene la scansione di 4000-TRNXFILE-GET, che si ferma alla prima carta
  maggiore di quella XREF.
