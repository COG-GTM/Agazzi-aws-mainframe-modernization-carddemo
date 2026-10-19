#!/usr/bin/env python3
"""Golden harness for x3-mqinquiry (CODATE01 / COACCT01, transactions CDRD / CDRA).

Compiles the legacy CICS+MQ programs from app/app-vsam-mq/cbl with GnuCOBOL, replacing
EXEC CICS blocks with CALLs to harness stubs and IBM MQ calls/copybooks with file-backed
stand-ins (see stubs/ and cpy/). Each line of online/<TRANID>/requests.txt is one MQGET
(padded to 1000 bytes); every MQPUT is captured as `<queue>|<correlid>|<1000-byte payload>|`
in online/<TRANID>/expected-replies.txt.

Usage: mqgolden.py [--verify]   (clock frozen at 2022-07-18 12:00:00 via faketime)
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODERN = HERE.parents[3]
REPO = MODERN.parent
APP = REPO / "app"
GOLDEN = MODERN / "carddemo-parity/src/test/resources/golden/x3-mqinquiry/online"
CLOCK = "2022-07-18 12:00:00"
COBC_FLAGS = ["-std=ibm", "-fnot-reserved=TITLE"]
PROGRAMS = [
    ("CODATE01", "CDRD", "CARD.DEMO.REQUEST.DATE"),
    ("COACCT01", "CDRA", "CARD.DEMO.REQUEST.ACCT"),
]

CICS_RE = re.compile(r"EXEC\s+CICS\s+(.*?)\s*END-EXEC", re.S)


def cics_replacement(body: str) -> str:
    words = body.split()
    cmd = words[0].upper()
    args = dict(re.findall(r"([A-Z0-9-]+)\s*\(\s*([^)]*?)\s*\)", body))
    if cmd == "RETRIEVE":
        return f"CALL 'CICSRETR' USING {args['INTO']} {args['RESP']}"
    if cmd == "READ":
        return (f"CALL 'ACCTREAD' USING {args['RIDFLD']} {args['INTO']} "
                f"{args['RESP']} {args['RESP2']}")
    if cmd == "FORMATTIME":
        return f"CALL 'FMTTIME' USING {args['MMDDYYYY']} {args['TIME']}"
    if cmd in ("ASKTIME", "SYNCPOINT", "RETURN"):
        return "CONTINUE"
    raise SystemExit(f"unsupported EXEC CICS {cmd}")


def wrap(stmt: str) -> str:
    """Re-flow a statement into fixed-format area B (columns 12-72)."""
    lines, cur = [], "           "
    for tok in stmt.split():
        if len(cur) + len(tok) + 1 > 72:
            lines.append(cur)
            cur = "               "
        cur += ("" if cur.isspace() else " ") + tok
    return "\n" + "\n".join(lines + [cur])


def adapt(src: Path, out: Path):
    lines = src.read_text(encoding="latin-1").splitlines()
    # Drop sequence/identification areas so multi-line EXEC blocks can be rewritten.
    text = "\n".join(("      " + l[6:72]) if len(l) > 6 else l for l in lines)
    text = re.sub(r"\n[ \t]*EXEC\s+CICS(.*?)END-EXEC",
                  lambda m: wrap(cics_replacement(m.group(1).strip())), text, flags=re.S)
    text = text.replace("DFHRESP(NORMAL)", "0").replace("DFHRESP(NOTFND)", "13")
    out.write_text(text + "\n", encoding="latin-1")


def run(cmd, **kw):
    subprocess.run(cmd, check=True, **kw)


def build(work: Path):
    lib = work / "lib"
    lib.mkdir()
    for stub in sorted((HERE / "stubs").glob("*.cbl")):
        run(["cobc", "-m", "-free", *COBC_FLAGS, "-o", str(lib / f"{stub.stem}.so"), str(stub)])
    exes = {}
    for prog, _, _ in PROGRAMS:
        adapted = work / f"{prog}.cbl"
        adapt(APP / "app-vsam-mq/cbl" / f"{prog}.cbl", adapted)
        exe = work / prog.lower()
        run(["cobc", "-x", *COBC_FLAGS, "-I", str(HERE / "cpy"), "-I", str(APP / "cpy"),
             "-o", str(exe), str(adapted)])
        exes[prog] = exe
    return lib, exes


def normalise(raw: bytes) -> bytes:
    out = []
    for line in raw.decode("latin-1").splitlines():
        queue, correl, payload = line[0:48].rstrip(), line[49:73].rstrip(), line[74:1074]
        out.append(f"{queue}|{correl}|{payload}|")
    return ("\n".join(out) + "\n").encode("latin-1")


def main():
    verify = "--verify" in sys.argv
    for tool in ("cobc", "faketime"):
        if not shutil.which(tool):
            raise SystemExit(f"{tool} not found: sudo apt-get install -y gnucobol faketime")
    failures = 0
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        lib, exes = build(work)
        for prog, tranid, trigq in PROGRAMS:
            case = GOLDEN / tranid
            out = work / f"{tranid}.out"
            out.touch()
            env = dict(os.environ, COB_LIBRARY_PATH=str(lib), TRIGQ=trigq,
                       MQREQ=str(case / "requests.txt"), MQOUT=str(out),
                       ACCTFILE=str(APP / "data/ASCII/acctdata.txt"), TZ="UTC")
            run(["faketime", CLOCK, str(exes[prog])], env=env)
            actual = normalise(out.read_bytes())
            expected = case / "expected-replies.txt"
            if verify:
                ok = expected.exists() and expected.read_bytes() == actual
                print(f"{tranid} ({prog}): {'OK' if ok else 'MISMATCH'}")
                failures += 0 if ok else 1
            else:
                expected.write_bytes(actual)
                print(f"{tranid} ({prog}): wrote {expected.relative_to(MODERN)}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
