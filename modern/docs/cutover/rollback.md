# Piano di rollback (Fase 5D)

Runbook: [CUTOVER_RUNBOOK.md §6](../CUTOVER_RUNBOOK.md#6-rollback-t-0--t5). Wrapper:
[`tools/cutover/rollback/relational-to-vsam.sh`](../../tools/cutover/rollback/relational-to-vsam.sh).

## 1. Criteri di attivazione

Il rollback è deciso da CM + BO entro la finestra T-0 → T+5 se si verifica almeno uno di:

| # | Criterio | Fonte |
|---|---|---|
| C1 | Riconciliazione T-0 (passo 0.8) non OK e non risolvibile entro 30 min | `reconciliation.md` |
| C2 | Smoke test `--strict` (0.12) fallito | `smoke.json` |
| C3 | Errore di dati confermato (saldi, interessi, estratti) non coperto da `PARITY_EXCEPTIONS.md` | ticket |
| C4 | Indisponibilità servizio online > 30 min o batch notturno non completato entro le 06:00 | CloudWatch / ECS |
| C5 | Incidente di sicurezza sul nuovo stack | SEC |

Dopo T+5 il rollback non è più previsto: si correggono i difetti in avanti.

Due modalità:
- **Light (R.3a)**: nessuna scrittura è avvenuta sul DB dopo lo switch (no-go fino a 0.11). I VSAM sono
  invariati dal CLOSEFIL: si riapre CICS (`OPENFIL`) e si rilasciano i job.
- **Full (R.3b)**: il DB ha ricevuto scritture. Si esporta lo stato PostgreSQL in formato VSAM con
  `relationalToVsam` e si ricarica con IDCAMS REPRO.

## 2. Contratto CLI di `relationalToVsam` (e `vsamToRelational`)

Job Spring Batch di 1B (`carddemo-batch-datalifecycle`), ancora in sviluppo. Interfaccia attesa dai
tool di cutover (lanciati con `tools/cutover/run-job.sh`, quindi `--spring.batch.job.name=<job>` +
parametri `key=value`):

```
run-job.sh relationalToVsam datasets=<LIST|ALL> format=<ebcdic|ascii> output=<dir|s3://bucket/prefix> [asOf=<ISO-8601>]
run-job.sh vsamToRelational datasets=<LIST|ALL> format=<ebcdic|ascii> input=<dir|s3://...> [mode=replace|merge]
```

| Parametro | Significato |
|---|---|
| `datasets` | nomi logici di [`datasets.yaml`](../../tools/cutover/datasets.yaml) separati da virgola (ACCTDATA, CARDDATA, CARDXREF, CUSTDATA, TCATBALF, DISCGRP, TRANTYPE, TRANCATG, TRANSACT, DALYTRAN, USRSEC) o `ALL` |
| `format` | `ebcdic`: RECFM=F, CP037, nessun terminatore, LRECL del copybook (`ZonedRecordCodec.ebcdic`); `ascii`: un record per riga come `app/data/ASCII` |
| `output` / `input` | directory o prefisso S3; un file per dataset con il nome della colonna `ebcdic`/`ascii` di `datasets.yaml` (es. `AWS.M2.CARDDEMO.ACCTDATA.PS`) |
| `asOf` | marcatore registrato nei parametri del job (identifica l'export) |
| `mode` | `replace` (TRUNCATE + load, T-14/T-0) o `merge` (upsert per chiave) |

Requisiti:
- ordine dei record = ordine di chiave primaria (come un REPRO da KSDS);
- encoding con i codec della Fase 0 (`CoreCodecs`), quindi byte-identico al layout del copybook;
- `USRSEC`: la password non è reversibile (BCrypt, PE-001): l'export scrive `SEC-USR-PWD` a spazi e il
  reload mainframe è seguito da reset password RACF/USRSEC (SEC) — la riconciliazione esclude la colonna;
- exit code 0 solo se tutti i dataset sono scritti; il job non modifica il DB (sola lettura).

## 3. Wrapper `relational-to-vsam.sh`

```
tools/cutover/rollback/relational-to-vsam.sh --out /data/rollback/2026-11-02 \
  [--format ebcdic] [--datasets ACCTDATA,...] [--db-url jdbc:...] [--base-url https://...] [--hlq AWS.M2.CARDDEMO]
```

1. verifica che il lato Java sia congelato (`GET /actuator/maintenance` con `closedFiles` non vuoto), altrimenti exit 4;
2. lancia `relationalToVsam` (exit 3 se il job non è ancora deployato → usare l'unload T-0 + R.3a solo se nessuna scrittura);
3. riconcilia l'export con il DB (`cutover.sh reconcile`): conteggi + checksum devono coincidere;
4. scrive `SHA256SUMS` e `ROLLBACK.jcl` (IDCAMS `REPRO INFILE(IN) OUTDATASET(<KSDS>) REPLACE` per ogni KSDS).

`--from-dir DIR` salta il passo 2 e verifica un export già prodotto (usato oggi nel self-test con il seed ASCII).

## 4. Ordine di esecuzione (full)

R.1 decisione → R.2 stop traffico Java → R.3b export + verifica → R.4 upload binario, `sha256sum -c`,
submit `ROLLBACK.jcl` → R.5 `OPENFIL`, release Control-M/CA-7, DNS → R.6 nuovo unload e riconciliazione
`--format ebcdic` contro il DB congelato, prova CC00/CAVW/CT02 da 3270.
Tempo stimato: 2 h (misurato a T-14, passo 14.10).
