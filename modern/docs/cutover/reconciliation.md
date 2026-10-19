# Riconciliazione e parallel run — note operative

Strumenti: `tools/cutover/cutover.sh reconcile` e `tools/cutover/cutover.sh compare` ([README](../../tools/cutover/README.md)).

## Cosa confronta la riconciliazione

Per ogni dataset di `datasets.yaml`: numero record del file vs righe della tabella, SHA-256 del file
sorgente, checksum SHA-256 delle righe canoniche (campi del copybook nell'ordine di chiave primaria,
numerici normalizzati alla scala del PIC) calcolato in modo identico sui due lati, e le prime 20 chiavi
diverse/mancanti/extra. Un `DbSnapshot` prima e dopo prova che il DB non è cambiato durante il controllo
(altrimenti esito FAILED anche con checksum uguali).

Tabelle con PK non presente nel copybook (`daily_transaction.seq_no`, identity): confronto come multiset
delle righe canoniche, indipendente dall'ordine di caricamento.

`TRANSACT` non ha file nel seed: solo con `--seed` un file mancante con `seed_empty: true` vale come vuoto;
in produzione l'unload è obbligatorio.

## Esito sul seed di oggi

- `app/data/ASCII` vs DB V1+V2: **11/11 MATCH** (incluso USRSEC con la password esclusa, PE-001).
- `app/data/EBCDIC` (CP037) vs DB: 9/11 MATCH (TRANSACT vuoto su entrambi i lati); **ACCTDATA** e **DISCGRP** differiscono perché i campioni
  EBCDIC del repository non coincidono con quelli ASCII da cui è generato il seed V2:
  - ACCTDATA record 50, `ACCT-ADDR-ZIP`: EBCDIC `ZEROAPR`, seed `A000000000`;
  - DISCGRP `DEFAULT   ` / `07` / `0001`, `DIS-INT-RATE`: EBCDIC `15.00`, seed `0.00`.
  Non è un difetto dei tool: a T-14 la sorgente è l'unload reale del giorno, non i campioni. Va però
  segnalato a 1B (quale dei due campioni è il riferimento per i goldens).

## Parallel run

Layout atteso per entrambi i lati: `<root>/<JOB>/<DD>.dat` + `RETURN-CODES.txt`, lo stesso prodotto dal
golden harness. Layout e LRECL degli output vengono dal `job.yaml` del golden del job; il confronto è
`RecordComparator` campo per campo e le differenze registrate in `PARITY_EXCEPTIONS.md` sono contate a
parte. Report: `reports/parallel-run/<data>/parallel-run-report.{md,json}`.
