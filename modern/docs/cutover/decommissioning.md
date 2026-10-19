# Decommissioning del mainframe CardDemo

Inventario completo, generato da `modern/ownership.yaml` + `docs/inventory/inventory.json`:
[decommission-inventory.md](decommission-inventory.md) (`tools/cutover/decommission_inventory.py`,
`--check` verifica che sia aggiornato). Totali: 25 transazioni CICS, 8 file e 21 mapset CSD,
10 job Control-M + 17 CA-7, 46 JCL + 2 PROC, 31 dataset (10 basi GDG), 44 programmi.

## 1. Prerequisiti (tutti obbligatori)

1. Finestra di rollback chiusa (T+5) senza rollback, verbale CM + BO.
2. 30 giorni di esercizio Java senza incidenti Sev1/2 e almeno una chiusura di fine mese
   (INTCALC, CREASTMT, TRANREPT mensile) eseguita in Java.
3. Per ogni riga dell'inventario, il workstream owner ha firmato che la sostituzione Java è in
   produzione (colonna `Owner`); le righe "(no source in app/)" (es. CA-7 `CLOSEFIL1/2`, `COCRDSEC`)
   vanno verificate da MFO sul sistema reale.
4. Requisiti legali/audit di retention confermati (default IaC: 10 anni, `glacier_archive_retention_days`).
5. Bucket `archive_bucket` (output Terraform) creato, con cifratura KMS, versioning, blocco accesso pubblico e
   lifecycle verso `DEEP_ARCHIVE`.
6. Nessun consumatore esterno residuo dei dataset (verifica SMF/RACF degli accessi negli ultimi 30 giorni).

## 2. Archiviazione su S3 Glacier

Per ogni dataset (VSAM, PS, ogni generazione GDG) e per le librerie sorgente:

1. `REPRO` → PS RECFM=F (VSAM) o copia diretta (PS/GDG); `LISTCAT ALL` salvato accanto.
2. Trasferimento **binario** (EBCDIC invariato) e checksum:
   `sha256sum * > SHA256SUMS && aws s3 cp --recursive . s3://$ARCHIVE_BUCKET/mainframe/<vsam|gdg>/<DSN>/ --storage-class DEEP_ARCHIVE`
3. Metadati: `aws s3api put-object-tagging` con `copybook`, `lrecl`, `recfm`, `owner`, `dsn`.
4. Verifica: `aws s3api head-object` per ogni oggetto (StorageClass `DEEP_ARCHIVE`), e una prova di
   restore + `cutover.sh reconcile --format ebcdic` su un campione (ACCTDATA) per dimostrare che l'archivio è
   leggibile con i codec Java.
5. Librerie: CSD (`DFHCSDUP EXTRACT`), JCL/PROC/copybook/COBOL/BMS (unload PDS), definizioni Control-M
   (export XML) e CA-7 (`LJOB`), SYSOUT degli ultimi 13 mesi → `s3://$ARCHIVE_BUCKET/mainframe/libs/`.

## 3. Ordine di spegnimento

| Wave | Quando | Owner | Cosa | Come |
|---|---|---|---|---|
| W1 | T+30 | MFO | Scheduler: job Control-M (`DAILY-TransactionBackup`, `WEEKLY-TransactionTypesDBRefresh`, `MONTHLY-InterestCalculation`) e CA-7 | da HOLD (T-0) a delete delle definizioni, dopo export |
| W2 | T+30 | MFO | CICS: transazioni, programmi, mapset, file del gruppo `CARDDEMO` | `CEDA REMOVE GROUP(CARDDEMO) LIST(...)`, poi `CEDA DELETE`; region CICS dedicata spenta |
| W3 | T+60 | MFO + DM | Dataset: archiviazione §2, poi `IDCAMS DELETE` (KSDS + AIX/PATH, PS, generazioni e basi GDG) | job IDCAMS generato da inventario §4; prima i PATH/AIX, poi i cluster, poi le generazioni, infine `DELETE ... GDG FORCE` |
| W4 | T+90 | MFO | Load module, librerie JCL/PROC/CNTL, utenze RACF e profili dataset `AWS.M2.CARDDEMO.*` | dopo archiviazione librerie |
| W5 | T+90 | PLT | Infrastruttura di transizione: bucket/ruoli del parallel run, ambiente `parallel` | `terraform destroy` dell'ambiente `parallel` (NON `prod`) |

Ogni wave: ticket CAB, verbale con elenco puntuale dall'inventario, conferma owner. Il codice in `app/`
resta nel repository come riferimento storico (non viene cancellato).

## 4. Criteri di chiusura

- tutte le righe dell'inventario spuntate con riferimento al ticket;
- archivio verificato (§2.4) e manifest `SHA256SUMS` firmato da DM;
- costi MIPS/licenze CardDemo azzerati nel report di capacity.
