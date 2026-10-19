# Golden 1b-datamigration

Fixtures of the `vsamToRelational` / `relationalToVsam` data migration
(`modern/carddemo-datamigration`).

| Path | Pinned by | Content |
|---|---|---|
| `vsamToRelational/metrics-ascii.json` | `AsciiRoundTripParityTest` (`-Pparity`) | count, checksums (`ACCT-CURR-BAL`, `TRAN-AMT`, `TRAN-CAT-BAL`, …), SHA-256 per column and per record of every `app/data/ASCII/*` extract |
| `vsamToRelational/metrics-ebcdic.json` | idem | same fingerprint for `app/data/EBCDIC/*.PS` (CP037) |
| `vsamToRelational/metrics-export.json` | idem | same fingerprint for `AWS.M2.CARDDEMO.EXPORT.DATA.PS` (CVEXPORT, COMP/COMP-3) |
| `vsamToRelational/reports/*.md` | `DataMigrationIT` (`-Pit`, PostgreSQL 16) | `reconciliation.md` (source vs DB) and `relational-to-vsam.md` (byte comparison) of the last regeneration |

Regenerate: `./mvnw -B -pl carddemo-datamigration -Pparity -Pit verify -Dgolden.update=true`.

Source data facts pinned here (not migration defects):

- `EXPORT.DATA.PS` carries `ACCT-CURR-BAL` = 0 and LOW-VALUES in `ACCT-ADDR-ZIP` /
  `ACCT-GROUP-ID` for accounts `00000000001` and `00000000050` (the KSDS extracts have
  194.00 / 492.00). LOW-VALUES are stored as spaces and reported as `LOW_VALUES` WARN.
- `ACCDATA.PS` and `ACCTDATA.PS` are byte-identical; the loader reads `ACCTDATA.PS`.
- `LISTCAT.txt` REC-TOTAL is informational: the catalog was taken after other runs
  (e.g. TCATBALF 100 vs 50 records in the extract) and is not part of the match.
