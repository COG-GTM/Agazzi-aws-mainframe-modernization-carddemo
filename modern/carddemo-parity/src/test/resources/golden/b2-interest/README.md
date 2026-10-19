# Golden masters b2-interest (INTCALC, TCATBALF, DISCGRP, PRTCATBL)

Generated with the original COBOL under GnuCOBOL (`modern/tools/golden/run-cobol.sh`):

```bash
cd modern
for j in INTCALC-SEED INTCALC-GROUPS INTCALC-EMPTY INTCALC-NODEFAULT INTCALC-NOACCT TCATBALF DISCGRP PRTCATBL; do
  ./tools/golden/run-cobol.sh ../b2-interest/$j --verify
done
```

The main INTCALC golden (daily cycle after POSTTRAN) is `golden/batch/INTCALC` (Fase 0). These
fixtures add the edge cases; the Java side is checked by the `@Tag("parity")` tests of
`carddemo-batch-interest` (`*GoldenParityTest`).

| Fixture | Scenario | Expected RC |
|---|---|---|
| `INTCALC-SEED` | immutable seed, zero balances, every category through the `DEFAULT` fallback | `STEP15 0` |
| `INTCALC-GROUPS` | explicit group hit (`A000000000`), `ZEROAPR` rate 0 (no SYSTRAN), unknown group → `DEFAULT`, rate 0 in `DEFAULT`, truncation (`0.07 * 25 / 1200 = 0.00`), last account never rewritten (legacy loop) | `STEP15 0` |
| `INTCALC-EMPTY` | empty TCATBALF | `STEP15 0` |
| `INTCALC-NODEFAULT` | no `DEFAULT` disclosure group → `ERROR READING DEFAULT DISCLOSURE GROUP`, CEE3ABD U999 | `STEP15 16` |
| `INTCALC-NOACCT` | balance of a missing account → `ACCOUNT NOT FOUND`, CEE3ABD U999 | `STEP15 16` |
| `TCATBALF` | STEP15 IDCAMS REPRO of an unsorted PS into the KSDS (key order) | `STEP15 0` |
| `DISCGRP` | STEP15 IDCAMS REPRO of the seed disclosure groups | `STEP15 0` |
| `PRTCATBL` | STEP05R REPRO KSDS → `BKUP`, STEP10R SORT (`SORTED`); `expected-dfsort/REPT.dat` is the OUTREC `EDIT=(TTTTTTTTT.TT)` report | `STEP05R 0`, `STEP10R 0` |

Notes:

- The harness emulates only `kind: program|sort|repro`. IDCAMS `DELETE`/`DEFINE` (STEP05/STEP10)
  are the empty cluster the harness starts from; the DFSORT `OUTREC ... EDIT` of PRTCATBL is not
  emulated, so `PRTCATBL/expected-dfsort/REPT.dat` was derived from `expected/SORTED.dat` with the
  DFSORT rules: `EDIT=(TTTTTTTTT.TT)` (absolute value, no sign position), the 41-byte OUTREC
  truncated to `SORTOUT LRECL=40`.
- GnuCOBOL 3.1 reads the z/OS overpunch `J..R` of the ASCII samples as **positive** digits, so no
  fixture uses negative input balances (z/OS would treat them as negative); negative truncation is
  covered by `InterestCalculatorTest`.
- `SYSOUT.txt` lines starting with `---- ` (harness framing) and `libcob: ` (GnuCOBOL runtime
  warnings at abend) are not part of the comparison.
