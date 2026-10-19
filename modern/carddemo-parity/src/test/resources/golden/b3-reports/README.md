# Golden masters b3-reports

Scenari TRANREPT/CREASTMT catturati con GnuCOBOL 3.1.2 dallo harness comune
(`modern/tools/golden/golden.py`) con radice puntata su questa cartella:

```bash
cd modern
carddemo-batch-reports/tools/run-cobol-b3.sh all            # ricattura
carddemo-batch-reports/tools/run-cobol-b3.sh all --verify   # zero diff
```

| Scenario | Copre |
|---|---|
| `TRANREPT` | report giornaliero 2022-07-01..2022-07-31 (pagine, totali conto/pagina/generale) |
| `TRANREPT-NODATA` | finestra date senza transazioni: TRANSACT.DALY e report vuoti, RC 0 |
| `CREASTMT` | statement testo (80) + HTML (100) per tutte le carte |
| `CREASTMT-EMPTY` | TRANSACT vuoto: CBSTM03A abend (CEE3ABD) in STEP040, RC 16 |

Gli input `golden:../batch/...` riusano i master di Fase 0. Il gate di parità è
`B3GoldenParityTest` (`@Tag("parity")`, `./mvnw -Pparity verify`), che confronta byte per byte
gli output e i return code degli step; TXT2PDF1 è confrontato sul testo estratto dal PDF.
