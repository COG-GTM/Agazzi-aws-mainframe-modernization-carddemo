# Reconciliation report — vsamToRelational

- source: `EBCDIC:app/data/EBCDIC (no extract for [TRANSACT])`
- generated: 2026-10-19-12.41.51.783629
- result: **MATCH**

## Counts and checksums

| Dataset | Table | File | LISTCAT REC-TOTAL | Source | Relational (rows / SQL COUNT) | Checksums source = DB | Changed rows | Result |
|---|---|---|---:|---:|---:|---|---:|---|
| TRANTYPE | `transaction_type` | `AWS.M2.CARDDEMO.TRANTYPE.PS` | — | 7 | 7 / 7 | — | 7 | MATCH |
| TRANCATG | `transaction_category` | `AWS.M2.CARDDEMO.TRANCATG.PS` | — | 18 | 18 / 18 | — | 18 | MATCH |
| DISCGRP | `disclosure_group` | `AWS.M2.CARDDEMO.DISCGRP.PS` | — | 51 | 51 / 51 | `DIS-INT-RATE` 390.00 = 390.00<br> | 51 | MATCH |
| CUSTDATA | `customer` | `AWS.M2.CARDDEMO.CUSTDATA.PS` | — | 50 | 50 / 50 | — | 50 | MATCH |
| ACCTDATA | `account` | `AWS.M2.CARDDEMO.ACCTDATA.PS` | — | 50 | 50 / 50 | `ACCT-CURR-BAL` 12269.00 = 12269.00<br>`ACCT-CREDIT-LIMIT` 233711.00 = 233711.00<br>`ACCT-CASH-CREDIT-LIMIT` 122148.00 = 122148.00<br> | 50 | MATCH |
| CARDDATA | `card` | `AWS.M2.CARDDEMO.CARDDATA.PS` | — | 50 | 50 / 50 | — | 50 | MATCH |
| CARDXREF | `card_xref` | `AWS.M2.CARDDEMO.CARDXREF.PS` | — | 50 | 50 / 50 | — | 50 | MATCH |
| TCATBALF | `tran_cat_balance` | `AWS.M2.CARDDEMO.TCATBALF.PS` | — | 50 | 50 / 50 | `TRAN-CAT-BAL` 0.00 = 0.00<br> | 50 | MATCH |
| DALYTRAN | `daily_transaction` | `AWS.M2.CARDDEMO.DALYTRAN.PS` | — | 300 | 300 / 300 | `DALYTRAN-AMT` 104801.54 = 104801.54<br> | 300 | MATCH |
| USRSEC | `app_user` | `AWS.M2.CARDDEMO.USRSEC.PS` | — | 10 | 10 / 10 | — | 10 | MATCH |
| USRSEC_ESDS | `usrsec_esds` | `AWS.M2.CARDDEMO.USRSEC.PS` | — | 10 | 10 / 10 | — | 10 | MATCH |
| USRSEC_RRDS | `usrsec_rrds` | `AWS.M2.CARDDEMO.USRSEC.PS` | — | 10 | 10 / 10 | — | 10 | MATCH |

## Column hashes (SHA-256, first 16 hex, source = DB)

### TRANTYPE — record hash `00e01dfa3a2144cc`

| Field | Hash | Match |
|---|---|---|
| `TRAN-TYPE@0` | `780f298670a9c634` | yes |
| `TRAN-TYPE-DESC@2` | `1aca1f7ad3e861e3` | yes |
| `FILLER@52` | `09e5bf5ed9505753` | yes |

### TRANCATG — record hash `b0e57c7d51e580f3`

| Field | Hash | Match |
|---|---|---|
| `TRAN-TYPE-CD@0` | `5fe50cd47b73d42d` | yes |
| `TRAN-CAT-CD@2` | `8976a3afcd718ce3` | yes |
| `TRAN-CAT-TYPE-DESC@6` | `1a3340210ce870b6` | yes |
| `FILLER@56` | `08e13684abf18b48` | yes |

### DISCGRP — record hash `9222a9e27b86cdb7`

| Field | Hash | Match |
|---|---|---|
| `DIS-ACCT-GROUP-ID@0` | `3a634a18fcf83d88` | yes |
| `DIS-TRAN-TYPE-CD@10` | `78c1246d0bded1a6` | yes |
| `DIS-TRAN-CAT-CD@12` | `794abeba9c27fb5e` | yes |
| `DIS-INT-RATE@16` | `655014bdea6982a8` | yes |
| `FILLER@22` | `20a81a31896d9735` | yes |

### CUSTDATA — record hash `36801ce3363967da`

| Field | Hash | Match |
|---|---|---|
| `CUST-ID@0` | `eb8be47b2c1f2312` | yes |
| `CUST-FIRST-NAME@9` | `a0aed3e4f84e09b0` | yes |
| `CUST-MIDDLE-NAME@34` | `49e025ea324ad45b` | yes |
| `CUST-LAST-NAME@59` | `c1e54cfd8613c604` | yes |
| `CUST-ADDR-LINE-1@84` | `2d8c4b5ab48aaeda` | yes |
| `CUST-ADDR-LINE-2@134` | `2b67ac98041f6e3e` | yes |
| `CUST-ADDR-LINE-3@184` | `88b1d8f87154f2f9` | yes |
| `CUST-ADDR-STATE-CD@234` | `fbb9f7a5ea0af6ae` | yes |
| `CUST-ADDR-COUNTRY-CD@236` | `9c3476d9f31de4dd` | yes |
| `CUST-ADDR-ZIP@239` | `b82d1978f16ade55` | yes |
| `CUST-PHONE-NUM-1@249` | `0cd786fd6eaff2b8` | yes |
| `CUST-PHONE-NUM-2@264` | `5826de3b85072949` | yes |
| `CUST-SSN@279` | `41bca031efaa97a9` | yes |
| `CUST-GOVT-ISSUED-ID@288` | `5a0a852dc90e7bbe` | yes |
| `CUST-DOB-YYYY-MM-DD@308` | `4f0d0f1ad11f58fa` | yes |
| `CUST-EFT-ACCOUNT-ID@318` | `026cb069c193102d` | yes |
| `CUST-PRI-CARD-HOLDER-IND@328` | `9e9b5cc39265662e` | yes |
| `CUST-FICO-CREDIT-SCORE@329` | `53c42dd709d010a3` | yes |
| `FILLER@332` | `6ae87cc2570d355d` | yes |

### ACCTDATA — record hash `b625fd984d283ae1`

| Field | Hash | Match |
|---|---|---|
| `ACCT-ID@0` | `eb8be47b2c1f2312` | yes |
| `ACCT-ACTIVE-STATUS@11` | `9e9b5cc39265662e` | yes |
| `ACCT-CURR-BAL@12` | `1bd6385517521367` | yes |
| `ACCT-CREDIT-LIMIT@24` | `17685376f1f40c31` | yes |
| `ACCT-CASH-CREDIT-LIMIT@36` | `d6792d7360dd2ab3` | yes |
| `ACCT-OPEN-DATE@48` | `7d8aa45ef946ea45` | yes |
| `ACCT-EXPIRAION-DATE@58` | `d20946b7a6cb587f` | yes |
| `ACCT-REISSUE-DATE@68` | `d20946b7a6cb587f` | yes |
| `ACCT-CURR-CYC-CREDIT@78` | `2cad184b0dab9b16` | yes |
| `ACCT-CURR-CYC-DEBIT@90` | `2cad184b0dab9b16` | yes |
| `ACCT-ADDR-ZIP@102` | `594e84da580b1f08` | yes |
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

### TCATBALF — record hash `cffeb17568638a8f`

| Field | Hash | Match |
|---|---|---|
| `TRANCAT-ACCT-ID@0` | `c9a0d5760bc467f6` | yes |
| `TRANCAT-TYPE-CD@11` | `1b487915fb559cdb` | yes |
| `TRANCAT-CD@13` | `1994b103729e0e7c` | yes |
| `TRAN-CAT-BAL@17` | `2cad184b0dab9b16` | yes |
| `FILLER@28` | `68b96a3a11766f03` | yes |

### DALYTRAN — record hash `5b25c7ccc8a5b471`

| Field | Hash | Match |
|---|---|---|
| `DALYTRAN-ID@0` | `04cb16fa70fa5919` | yes |
| `DALYTRAN-TYPE-CD@16` | `f1b2dafebe4294dd` | yes |
| `DALYTRAN-CAT-CD@18` | `d3efc416288f9562` | yes |
| `DALYTRAN-SOURCE@22` | `ce3c51e0cb14f87c` | yes |
| `DALYTRAN-DESC@32` | `75f9e4046281577b` | yes |
| `DALYTRAN-AMT@132` | `c8ca7fcb68a383c5` | yes |
| `DALYTRAN-MERCHANT-ID@143` | `1a7ee1ee1934bce2` | yes |
| `DALYTRAN-MERCHANT-NAME@152` | `0755438aeb6276cd` | yes |
| `DALYTRAN-MERCHANT-CITY@202` | `3cba2b02127f3f3f` | yes |
| `DALYTRAN-MERCHANT-ZIP@252` | `301725b94ffffb57` | yes |
| `DALYTRAN-CARD-NUM@262` | `d27fbce78f29fedc` | yes |
| `DALYTRAN-ORIG-TS@278` | `5a2add9e820a8b5d` | yes |
| `DALYTRAN-PROC-TS@304` | `440cdeadaf9e51ed` | yes |
| `FILLER@330` | `03f6410995f9b66b` | yes |

### USRSEC — record hash `2ada5e8b2cb11ef7`

| Field | Hash | Match |
|---|---|---|
| `SEC-USR-ID@0` | `0e016fbe9f52303a` | yes |
| `SEC-USR-FNAME@8` | `ff5b635d8545d5e8` | yes |
| `SEC-USR-LNAME@28` | `b99e4119dfdb48aa` | yes |
| `SEC-USR-PWD@48` | `6801970358e129a7` | yes |
| `SEC-USR-TYPE@56` | `bae4e1e49e2540af` | yes |
| `SEC-USR-FILLER@57` | `48577c4149b0add4` | yes |

### USRSEC_ESDS — record hash `2ada5e8b2cb11ef7`

| Field | Hash | Match |
|---|---|---|
| `SEC-USR-ID@0` | `0e016fbe9f52303a` | yes |
| `SEC-USR-FNAME@8` | `ff5b635d8545d5e8` | yes |
| `SEC-USR-LNAME@28` | `b99e4119dfdb48aa` | yes |
| `SEC-USR-PWD@48` | `6801970358e129a7` | yes |
| `SEC-USR-TYPE@56` | `bae4e1e49e2540af` | yes |
| `SEC-USR-FILLER@57` | `48577c4149b0add4` | yes |

### USRSEC_RRDS — record hash `2ada5e8b2cb11ef7`

| Field | Hash | Match |
|---|---|---|
| `SEC-USR-ID@0` | `0e016fbe9f52303a` | yes |
| `SEC-USR-FNAME@8` | `ff5b635d8545d5e8` | yes |
| `SEC-USR-LNAME@28` | `b99e4119dfdb48aa` | yes |
| `SEC-USR-PWD@48` | `6801970358e129a7` | yes |
| `SEC-USR-TYPE@56` | `bae4e1e49e2540af` | yes |
| `SEC-USR-FILLER@57` | `48577c4149b0add4` | yes |

## Validation findings

None.
