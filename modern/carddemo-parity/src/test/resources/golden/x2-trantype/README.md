# Golden X2 — tipi transazione DB2 (`app/app-transaction-type-db2`)

| Path | Contenuto | Test |
|---|---|---|
| `online/CTLI/*.yaml` | 15 scenari COTRTLIC (filtri, keyset avanti/indietro, U/D + F10, errori, PF2/PF3) | `X2OnlineGoldenParityTest` (parity), `TrantypeEnabledIT` (it, PostgreSQL) |
| `online/CTTU/*.yaml` | 15 scenari COTRTUPC (fetch, add F5, update F5, delete F4, F12, errori) | idem |
| `MNTTRDB2/INPFILE` | input RECFM=F LRECL=53 (A/U/D/*, duplicato, +100, -532, tipo errato) | `Mnttrdb2GoldenParityTest`, `TrantypeEnabledIT` |
| `MNTTRDB2/SYSOUT`, `RC`, `TRANTYPE.after` | output del COBTUPDT **originale** compilato con GnuCOBOL 3.1.2 | idem |

Formato YAML identico a `golden/online/` (`OnlineScenario`): ogni step è una POST su
`/api/v1/screens/<screen>`, `dbDelta` è `none`, `any` o la lista `KIND tabella chiave`.
Lo stato pseudo-conversazionale vive nella sessione HTTP; i client senza cookie (HTTP Basic,
come `OnlineScenarioRunner`) hanno una conversazione per utente.

`MNTTRDB2/harness/run.sh [--verify]` rigenera/verifica gli output: `prepare.py` sostituisce i
blocchi `EXEC SQL` di COBTUPDT con `CALL 'SQLSTUB'` (`sqlstub.c`: tabella da seed V2, SQLCODE
0/+100/-803/-532 come DB2 con FK `ON DELETE RESTRICT` di TRNTYCAT); la logica COBOL (DISPLAY,
`WS-RETURN-MSG` mai ripulito, RC 4 non bloccante) gira invariata.

Divergenze note (testo DB2): il testo DSNTIAC dopo `SQLCODE:` è sostituito da un testo fisso
(`ROW NOT FOUND`, `TRANSACTION_CATEGORY ROWS DEPEND ON THIS TYPE`, `DUPLICATE KEY`); i messaggi
info non sono centrati nei 45 caratteri di `WS-INFO-MSG` (il DTO li espone già trimmati).
