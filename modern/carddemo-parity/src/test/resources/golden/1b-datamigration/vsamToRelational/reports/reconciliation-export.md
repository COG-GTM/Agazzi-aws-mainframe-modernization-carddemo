# Reconciliation report — vsamToRelational

- source: `EXPORT:app/data/EBCDIC (no extract for [TRANTYPE, TRANCATG, DISCGRP, TCATBALF, DALYTRAN, USRSEC, USRSEC_ESDS, USRSEC_RRDS])`
- generated: 2026-10-19-12.41.52.270481
- result: **MATCH**

## Counts and checksums

| Dataset | Table | File | LISTCAT REC-TOTAL | Source | Relational (rows / SQL COUNT) | Checksums source = DB | Changed rows | Result |
|---|---|---|---:|---:|---:|---|---:|---|
| CUSTDATA | `customer` | `AWS.M2.CARDDEMO.EXPORT.DATA.PS` | — | 50 | 50 / 50 | — | 50 | MATCH |
| ACCTDATA | `account` | `AWS.M2.CARDDEMO.EXPORT.DATA.PS` | — | 50 | 50 / 50 | `ACCT-CURR-BAL` 11583.00 = 11583.00<br>`ACCT-CREDIT-LIMIT` 233711.00 = 233711.00<br>`ACCT-CASH-CREDIT-LIMIT` 122148.00 = 122148.00<br> | 50 | MATCH |
| CARDDATA | `card` | `AWS.M2.CARDDEMO.EXPORT.DATA.PS` | — | 50 | 50 / 50 | — | 50 | MATCH |
| CARDXREF | `card_xref` | `AWS.M2.CARDDEMO.EXPORT.DATA.PS` | — | 50 | 50 / 50 | — | 50 | MATCH |
| TRANSACT | `transaction` | `AWS.M2.CARDDEMO.EXPORT.DATA.PS` | — | 300 | 300 / 300 | `TRAN-AMT` 104801.54 = 104801.54<br> | 300 | MATCH |

## Column hashes (SHA-256, first 16 hex, source = DB)

### CUSTDATA — record hash `3102b9dec1141d07`

| Field | Hash | Match |
|---|---|---|
| `CUST-ID@0` | `eb8be47b2c1f2312` | yes |
| `CUST-FIRST-NAME@9` | `0a40ecfde4e6129d` | yes |
| `CUST-MIDDLE-NAME@34` | `fe8e1aae9aa0cae8` | yes |
| `CUST-LAST-NAME@59` | `464e224fcb67f6d6` | yes |
| `CUST-ADDR-LINE-1@84` | `e96ab4c037f535bb` | yes |
| `CUST-ADDR-LINE-2@134` | `50879cc6f9fae2d8` | yes |
| `CUST-ADDR-LINE-3@184` | `dad4e6690934dcee` | yes |
| `CUST-ADDR-STATE-CD@234` | `444f1990c6e570d6` | yes |
| `CUST-ADDR-COUNTRY-CD@236` | `9c3476d9f31de4dd` | yes |
| `CUST-ADDR-ZIP@239` | `f22b5406a073be46` | yes |
| `CUST-PHONE-NUM-1@249` | `e5d66e49df0bd750` | yes |
| `CUST-PHONE-NUM-2@264` | `a5d7ff3a6ec84a5c` | yes |
| `CUST-SSN@279` | `3e4e5845ab6244d1` | yes |
| `CUST-GOVT-ISSUED-ID@288` | `5a0a852dc90e7bbe` | yes |
| `CUST-DOB-YYYY-MM-DD@308` | `ca57f3531c136840` | yes |
| `CUST-EFT-ACCOUNT-ID@318` | `026cb069c193102d` | yes |
| `CUST-PRI-CARD-HOLDER-IND@328` | `9e9b5cc39265662e` | yes |
| `CUST-FICO-CREDIT-SCORE@329` | `cdfe8ae4c735290f` | yes |
| `FILLER@332` | `6ae87cc2570d355d` | yes |

### ACCTDATA — record hash `c3288b8e5b630e3b`

| Field | Hash | Match |
|---|---|---|
| `ACCT-ID@0` | `eb8be47b2c1f2312` | yes |
| `ACCT-ACTIVE-STATUS@11` | `9e9b5cc39265662e` | yes |
| `ACCT-CURR-BAL@12` | `dddcc62734010726` | yes |
| `ACCT-CREDIT-LIMIT@24` | `17685376f1f40c31` | yes |
| `ACCT-CASH-CREDIT-LIMIT@36` | `d6792d7360dd2ab3` | yes |
| `ACCT-OPEN-DATE@48` | `0cba614368c6b2aa` | yes |
| `ACCT-EXPIRAION-DATE@58` | `3ad5ea88ea362939` | yes |
| `ACCT-REISSUE-DATE@68` | `d20946b7a6cb587f` | yes |
| `ACCT-CURR-CYC-CREDIT@78` | `2cad184b0dab9b16` | yes |
| `ACCT-CURR-CYC-DEBIT@90` | `2cad184b0dab9b16` | yes |
| `ACCT-ADDR-ZIP@102` | `85700bdb9f9a0030` | yes |
| `ACCT-GROUP-ID@112` | `a439850f2d9f2104` | yes |
| `FILLER@122` | `ec5bcd5a121d56fc` | yes |

### CARDDATA — record hash `031dda48176a5ce9`

| Field | Hash | Match |
|---|---|---|
| `CARD-NUM@0` | `ac005e1a10830154` | yes |
| `CARD-ACCT-ID@16` | `217bf7d65f6f4590` | yes |
| `CARD-CVV-CD@27` | `86677b6bd1eca966` | yes |
| `CARD-EMBOSSED-NAME@30` | `4747b97f500dd20a` | yes |
| `CARD-EXPIRAION-DATE@80` | `e1f0a265a27cb7d3` | yes |
| `CARD-ACTIVE-STATUS@90` | `9e9b5cc39265662e` | yes |
| `FILLER@91` | `9097fadcadbe7b79` | yes |

### CARDXREF — record hash `d49145cf821e7aae`

| Field | Hash | Match |
|---|---|---|
| `XREF-CARD-NUM@0` | `ac005e1a10830154` | yes |
| `XREF-CUST-ID@16` | `217bf7d65f6f4590` | yes |
| `XREF-ACCT-ID@25` | `217bf7d65f6f4590` | yes |
| `FILLER@36` | `1e45b4adf0c6387c` | yes |

### TRANSACT — record hash `5b25c7ccc8a5b471`

| Field | Hash | Match |
|---|---|---|
| `TRAN-ID@0` | `04cb16fa70fa5919` | yes |
| `TRAN-TYPE-CD@16` | `f1b2dafebe4294dd` | yes |
| `TRAN-CAT-CD@18` | `d3efc416288f9562` | yes |
| `TRAN-SOURCE@22` | `ce3c51e0cb14f87c` | yes |
| `TRAN-DESC@32` | `75f9e4046281577b` | yes |
| `TRAN-AMT@132` | `c8ca7fcb68a383c5` | yes |
| `TRAN-MERCHANT-ID@143` | `1a7ee1ee1934bce2` | yes |
| `TRAN-MERCHANT-NAME@152` | `0755438aeb6276cd` | yes |
| `TRAN-MERCHANT-CITY@202` | `3cba2b02127f3f3f` | yes |
| `TRAN-MERCHANT-ZIP@252` | `301725b94ffffb57` | yes |
| `TRAN-CARD-NUM@262` | `d27fbce78f29fedc` | yes |
| `TRAN-ORIG-TS@278` | `5a2add9e820a8b5d` | yes |
| `TRAN-PROC-TS@304` | `440cdeadaf9e51ed` | yes |
| `FILLER@330` | `03f6410995f9b66b` | yes |

## Validation findings

| Severity | Dataset | Record | Rule | Field | Message |
|---|---|---:|---|---|---|
| WARN | ACCTDATA | 1 | LOW_VALUES |  | LOW-VALUES replaced by spaces in ACCT-ADDR-ZIP, ACCT-GROUP-ID |
| WARN | ACCTDATA | 50 | LOW_VALUES |  | LOW-VALUES replaced by spaces in ACCT-ADDR-ZIP, ACCT-GROUP-ID |
