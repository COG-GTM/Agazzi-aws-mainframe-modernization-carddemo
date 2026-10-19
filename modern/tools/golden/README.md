# Golden harness (plan §0.5 / §11)

`run-cobol.sh <JOB> [--verify]` compiles the **original** COBOL from `app/cbl` with
GnuCOBOL 3.x (`-std=ibm`), loads the sample data from `app/data/ASCII` into BDB
indexed files (KSDS emulation), runs the JCL steps described in
`carddemo-parity/src/test/resources/golden/batch/<JOB>/job.yaml` and writes every
output dataset into `<JOB>/expected/`. `--verify` re-runs and fails on any byte
difference. `run-cobol.sh all [--verify]` runs the jobs listed in `ORDER`
(POSTTRAN → INTCALC → COMBTRAN → TRANREPT/CREASTMT, READACCT).

Determinism: the business clock is frozen with `faketime` + `COB_CURRENT_DATE`
(`business_timestamp` in `job.yaml`), `TZ=UTC`, `LC_ALL=C`.

## Harness-only adaptations (never applied to `app/`)
| Item | Adaptation |
|---|---|
| `CUSTREC.cpy` | literal TABs replaced by spaces in the working copy (`target/golden-work/*/cpy`) |
| `CBEXPORT`/`CBIMPORT` | `PIC 9(n) COMP` used as `RECORD KEY` → DISPLAY in the working copy |
| `CBSTM03A` | z/OS PSA→TCB→TIOT control-block walk replaced by a `DISPLAY` of job/step |
| `CEE3ABD` | `stubs/CEE3ABD.cbl`: prints the abend code and ends with RC=16 |
| `COBDATFT` | `stubs/COBDATFT.cbl`: COBOL port of `app/asm/COBDATFT.asm` |
| `MVSWAIT` | `stubs/MVSWAIT.cbl`: `C$SLEEP`, skipped when `GOLDEN_NOWAIT=Y` |
| `EXEC PGM=,PARM=` | `stubs/JCLDRV.cbl` builds the LE parameter list (halfword length + text) |
| `SORT`, `IDCAMS REPRO` | emulated in `golden.py` (`kind: sort|repro`) |

## Output format
* fixed-length datasets: `<DS>.dat`, one record per line (LRECL bytes + `\n`);
  KSDS are unloaded in key order;
* binary/variable datasets (`org: raw`): `<DS>.bin`, byte-exact;
* `SYSOUT.txt` (DISPLAY output of every step) and `RETURN-CODES.txt`.
