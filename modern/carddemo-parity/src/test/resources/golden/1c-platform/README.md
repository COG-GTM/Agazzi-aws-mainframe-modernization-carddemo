# Golden masters of workstream 1c-platform

| Job | What it pins | Java counterpart |
|---|---|---|
| `CSUTLDTC/` | original `app/cbl/CSUTLDTC.cbl` called by the harness driver `src/DTCDRV.cbl` for every row of `input/CASES.txt`; LE `CEEDAYS` is emulated by `src/CEEDAYS.cbl` | `com.carddemo.platform.time.Csutldtc` / `CeeDays` |
| `READACCT/` | `app/jcl/READACCT.jcl` (`CBACT01C` → `COBDATFT`, type 2 → 2) on the seed `acctdata.txt` | `com.carddemo.platform.time.CobolDateFormatter` |

Capture / verify (GnuCOBOL 3.x + faketime, same harness as `tools/golden/run-cobol.sh`):

```bash
cd modern
python3 carddemo-parity/src/test/resources/golden/1c-platform/capture.py CSUTLDTC [--verify]
python3 carddemo-parity/src/test/resources/golden/1c-platform/capture.py READACCT [--verify]
```

`capture.py` reuses `tools/golden/golden.py` unchanged; it only adds `<JOB>/src` to the COBOL
source search path (harness-only programs that must not live under `app/`). The parity tests in
`carddemo-platform` (`@Tag("parity")`) compare the Java ports with `expected/` and re-run the
capture with `--verify` when `cobc` is available (mandatory with `CARDDEMO_REQUIRE_COBOL=true`).

`RESULTS.dat` records are 104 bytes (`date X(10)`, `mask X(10)`, `LS-RESULT X(80)`, `RETURN-CODE
9(4)`) + `\n`; `LS-RESULT` carries the binary halfword `X'000A'` that `CSUTLDTC` moves into
`WS-DATE`, so read the file by fixed length, not by line.

`CEEDAYS.cbl` covers the picture tokens `YYYY YY MM DD DDD` + literal separators, the feedback
codes CEE2507/2508/2513/2517/2518/2520 and the default `CEESCEN` 80-year window; the Lillian
number itself is not part of `LS-RESULT` (`CSUTLDTC` discards it) and is pinned by unit tests.
