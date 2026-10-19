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
| PE-001 | COSGN00C | SEC-USR-PWD | password in chiaro in USRSEC (PIC X(08)) | hash BCrypt in `app_user.password_hash` | sicurezza (piano §3.1, Spring Security 6) | leader migrazione | proposed |
