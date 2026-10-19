# Golden x1-authorization

| Path | Origin | Java check |
|---|---|---|
| `DBPAUTP0/expected/PAUTSUM0.bin`, `PAUTDTL1.bin` | `app/.../data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat` decoded (HD unload: 2-byte length, segment name @10, data @39) | `X1BatchParityTest#dbpautp0...` (load raw -> tables -> unload) |
| `CBPAUP0J/<scenario>/` | `CBPAUP0C.cbl` compiled with GnuCOBOL 3.1.2, DL/I calls stubbed on the DBPAUTP0 segments, FAKETIME = `BUSINESS-DATE.txt`, SYSIN = `SYSIN.txt` | `X1BatchParityTest#cbpaup0jPurgeMatchesCobol` (segments byte-for-byte + SYSOUT counters) |
| `online/CPVS/*.yaml`, `online/CPVD/*.yaml` | `OnlineScenario` format, expectations taken from `COPAUS0C`/`COPAUS1C`/`COPAUS2C` (no CICS runtime available) | `X1OnlineScenariosParityTest` |

Regenerate the batch goldens: `modern/carddemo-ext-authorization/src/test/cobol/run-x1-golden.sh [--verify]`.

Segment files: `PAUTSUM0.bin` = 100-byte roots; `PAUTDTL1.bin` = 6-byte packed parent `ACCNTID` + 200-byte child.

## Known deviations (to be copied to `modern/PARITY_EXCEPTIONS.md` by the leader)
- **X1-1** — the raw unload contains one root whose `PA-ACCT-ID` is `x'404040404040'` (not packed decimal). The Java loader rejects it (`LoadReport.rejected`); the parity test filters it from the COBOL expectation and from the `TOTAL SUMMARY READ` / `SUMMARY REC DELETED` counters.
- **X1-2** — LOW-VALUES in `PA-AUTH-STATUS` / `PA-ACCOUNT-STATUS` (PIC X) are stored as spaces (PostgreSQL text cannot hold NUL); the parity test normalises x'00' to a space in those 11 bytes.
- COBOL behaviours kept on purpose (not deviations): in CPVS a short last page leaves `NEXT-PAGE-FLG = 'Y'` (PF8 shows an empty page) and `PROCESS-PAGE-FORWARD` re-increments `CDEMO-CPVS-PAGE-NUM`, so PF7 after paging never shows the top-of-page message; CBPAUP0C decrements the counters but never `REPL`s the summary.
