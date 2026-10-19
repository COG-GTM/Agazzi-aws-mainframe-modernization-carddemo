# carddemo-batch-interest (B2 — interessi e saldi per categoria)

Spring Batch migration of `app/cbl/CBACT04C.cbl` and `app/jcl/{INTCALC,TCATBALF,DISCGRP,PRTCATBL}.jcl`.
Job = JCL name, step = JCL step name, `JobParameters` replace `PARM` / DD names.

| Job | Steps | Job parameters | Outputs |
|---|---|---|---|
| `INTCALC` | `STEP15` (CBACT04C) | `PARM` (e.g. `2022071800`), `SYSOUT` (optional file) | `account` rows (interest added, cycle credit/debit zeroed), GDG `AWS.M2.CARDDEMO.SYSTRAN(+1)` |
| `TCATBALF` | `STEP05` DELETE, `STEP10` DEFINE, `STEP15` REPRO | `TCATBAL` (TCATBALF.PS file) | `tran_cat_balance` reloaded |
| `DISCGRP` | `STEP05` DELETE, `STEP10` DEFINE, `STEP15` REPRO | `DISCGRP` (DISCGRP.PS file) | `disclosure_group` reloaded |
| `PRTCATBL` | `DELDEF`, `STEP05R` (REPROC), `STEP10R` (SORT) | `REPT` (report file) | GDG `AWS.M2.CARDDEMO.TCATBALF.BKUP(+1)`, `REPT` (LRECL 40) |

## Command line

```bash
java -jar carddemo-app/target/carddemo-app-*.jar \
  --spring.batch.job.enabled=true --spring.batch.job.name=INTCALC PARM=2022071800
```

The process exit code is the JCL return code (`B2JobListener`): `0` OK, `12` IDCAMS error
(malformed/duplicate/missing input), `16` abend (`CEE3ABD U999`: missing account, xref or
`DEFAULT` disclosure group; DFSORT failure). Re-running a FAILED job with the same parameters
restarts it from the failed step (`RunIdIncrementer`: a new run otherwise).

## Behaviour notes

- `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200` → `CobolMath.divide(..., 9, 2, TRUNCATE)` (no `ROUNDED`).
- Missing disclosure group → `DEFAULT` group with the same type/category (two `DISPLAY` lines).
- `TRAN-ID` = `PARM` (X(10)) + 6-digit sequence; timestamps `YYYY-MM-DD-HH.MM.SS.hh0000` from
  `BusinessClock`.
- Legacy quirk kept for parity: the last account of TCATBALF is never rewritten (the `ELSE`
  branch of the CBACT04C main loop is unreachable).
- Each step is one transaction: an abend rolls the step back and discards the new GDG generation
  (`DISP=(NEW,CATLG,DELETE)`), whereas VSAM would keep the accounts rewritten before the abend.

## Tests

- unit (`./mvnw -B verify`): calculator, return codes, restart / empty / malformed input, CLI.
- parity (`-Pparity`, `@Tag("parity")`): `*GoldenParityTest` against `golden/batch/INTCALC` and
  `golden/b2-interest/*` (file-in/file-out, DB delta, SYSOUT, RC).
- IT (`-Pit`): `IntcalcPostgresIT` on PostgreSQL 16 (Testcontainers).
