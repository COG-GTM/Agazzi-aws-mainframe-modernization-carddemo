#!/usr/bin/env python3
"""CardDemo golden-master harness (plan §0.5 / §11).

Runs the ORIGINAL COBOL batch programs from app/cbl under GnuCOBOL on the
sample data and captures every output dataset as a golden master.

Nothing under app/ is modified: copybooks that need GnuCOBOL adaptations are
copied into the work directory and patched there.

Usage: golden.py <JOB> [--verify] [--work DIR]
  <JOB>     directory name under carddemo-parity/src/test/resources/golden/batch
  --verify  re-run and compare against the committed expected/ (exit 1 on diff)
"""
import argparse
import filecmp
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

import yaml

HERE = Path(__file__).resolve().parent
MODERN = HERE.parent.parent
REPO = MODERN.parent
APP = REPO / "app"
GOLDEN_ROOT = MODERN / "carddemo-parity" / "src" / "test" / "resources" / "golden" / "batch"
COBC_FLAGS = ["-std=ibm", "-fnot-reserved=TITLE"]


def log(msg):
    print(f"[golden] {msg}", flush=True)


def run(cmd, env=None, cwd=None, stdout=None, check=True):
    res = subprocess.run(cmd, env=env, cwd=cwd, stdout=stdout, stderr=subprocess.STDOUT
                         if stdout else None)
    if check and res.returncode != 0:
        raise SystemExit(f"command failed ({res.returncode}): {' '.join(map(str, cmd))}")
    return res.returncode


# ---------------------------------------------------------------- copybooks
def prepare_copybooks(work):
    """Copy app/cpy into work/cpy applying harness-only GnuCOBOL adaptations."""
    cpy = work / "cpy"
    if cpy.exists():
        shutil.rmtree(cpy)
    shutil.copytree(APP / "cpy", cpy)
    # CUSTREC.cpy contains literal TABs in fixed-format columns: normalise.
    custrec = cpy / "CUSTREC.cpy"
    if custrec.exists():
        lines = []
        for line in custrec.read_text(encoding="latin-1").splitlines():
            line = re.sub(r"^\t+ *", " " * 11, line).replace("\t", " ").rstrip()
            lines.append(line)
        custrec.write_text("\n".join(lines) + "\n", encoding="latin-1")
    return cpy


def adapt_source(src: Path, out: Path):
    """Harness-only source adaptations (never written back to app/)."""
    text = src.read_text(encoding="latin-1")
    if src.stem.upper() in ("CBEXPORT", "CBIMPORT"):
        # GnuCOBOL rejects numeric COMP items as RECORD KEY: make them DISPLAY.
        text = re.sub(r"(PIC\s+9\(\d+\))\s+COMP(?:-3)?\s*\.", r"\1.", text)
    if src.stem.upper() == "CBSTM03A":
        # z/OS PSA->TCB->TIOT control-block walk cannot run off-mainframe:
        # replace it with the equivalent DISPLAY of job/step (harness only).
        text, n = re.subn(
            r"(\n)[ \t]+SET ADDRESS OF PSA-BLOCK +TO PSAPTR\..*?(\n[ \t]+OPEN OUTPUT STMT-FILE)",
            r"\1           DISPLAY 'Running JCL : CREASTMT Step STEP040 '.\2",
            text, flags=re.S)
        if n != 1:
            raise SystemExit("CBSTM03A TIOT adaptation did not apply")
    out.write_text(text, encoding="latin-1")


def find_source(program):
    for ext in (".cbl", ".CBL"):
        p = APP / "cbl" / f"{program}{ext}"
        if p.exists():
            return p
    raise SystemExit(f"COBOL source not found for {program}")


def compile_module(src, lib, cpy, work):
    target = lib / f"{src.stem.upper()}.so"
    adapted = work / "src" / src.name
    adapted.parent.mkdir(parents=True, exist_ok=True)
    adapt_source(src, adapted)
    run(["cobc", "-m", *COBC_FLAGS, "-I", str(cpy), "-o", str(target), str(adapted)])


def compile_all(job, work):
    cpy = prepare_copybooks(work)
    lib = work / "lib"
    lib.mkdir(parents=True, exist_ok=True)
    for stub in sorted((HERE / "stubs").glob("*.cbl")):
        if stub.stem == "JCLDRV":
            run(["cobc", "-x", *COBC_FLAGS, "-o", str(work / "jcldrv"), str(stub)])
        else:
            compile_module(stub, lib, cpy, work)
    programs = set()
    for step in job["steps"]:
        if step.get("program"):
            programs.add(step["program"])
        programs.update(step.get("calls", []))
    for program in sorted(programs):
        log(f"compile {program}")
        compile_module(find_source(program), lib, cpy, work)
    return lib


# ---------------------------------------------------------------- datasets
KSUTIL = """       IDENTIFICATION DIVISION.
       PROGRAM-ID. KSUTIL.
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT PS-FILE ASSIGN TO PSFILE
               ORGANIZATION IS SEQUENTIAL
               FILE STATUS IS PS-STAT.
           SELECT KS-FILE ASSIGN TO KSFILE
               ORGANIZATION IS INDEXED
               ACCESS MODE IS DYNAMIC
               RECORD KEY IS KS-KEY
{aix}               FILE STATUS IS KS-STAT.
       DATA DIVISION.
       FILE SECTION.
       FD PS-FILE.
       01 PS-REC PIC X({lrecl}).
       FD KS-FILE.
       01 KS-REC.
{layout}       WORKING-STORAGE SECTION.
       01 PS-STAT PIC XX.
       01 KS-STAT PIC XX.
       01 WS-MODE PIC X(8).
       01 WS-EOF  PIC X VALUE 'N'.
       PROCEDURE DIVISION.
           ACCEPT WS-MODE FROM ARGUMENT-VALUE
           IF WS-MODE = 'LOAD'
               OPEN INPUT PS-FILE OUTPUT KS-FILE
               PERFORM UNTIL WS-EOF = 'Y'
                   READ PS-FILE
                       AT END MOVE 'Y' TO WS-EOF
                       NOT AT END
                           MOVE PS-REC TO KS-REC
                           WRITE KS-REC
                           IF KS-STAT NOT = '00' AND KS-STAT NOT = '02'
                               DISPLAY 'KSUTIL WRITE STATUS ' KS-STAT
                               MOVE 8 TO RETURN-CODE
                           END-IF
                   END-READ
               END-PERFORM
           ELSE
               OPEN INPUT KS-FILE OUTPUT PS-FILE
               PERFORM UNTIL WS-EOF = 'Y'
                   READ KS-FILE NEXT
                       AT END MOVE 'Y' TO WS-EOF
                       NOT AT END
                           MOVE KS-REC TO PS-REC
                           WRITE PS-REC
                   END-READ
               END-PERFORM
           END-IF
           CLOSE PS-FILE KS-FILE
           STOP RUN.
"""


def ksutil(ds, work):
    lrecl, (koff, klen) = ds["lrecl"], ds["key"]
    aix = ds.get("aix", [])
    fields = [(koff, klen, "KS-KEY")] + [(a[0], a[1], f"KS-AIX{i}") for i, a in enumerate(aix)]
    fields.sort()
    layout, pos = "", 0
    for off, length, name in fields:
        if off > pos:
            layout += f"          05 FILLER PIC X({off - pos}).\n"
        layout += f"          05 {name} PIC X({length}).\n"
        pos = off + length
    if lrecl > pos:
        layout += f"          05 FILLER PIC X({lrecl - pos}).\n"
    aix_clause = "".join(
        f"               ALTERNATE RECORD KEY IS KS-AIX{i}"
        f"{' WITH DUPLICATES' if (len(a) > 2 and a[2]) else ''}\n"
        for i, a in enumerate(aix))
    tag = f"ks_{lrecl}_{koff}_{klen}_" + "_".join(f"{a[0]}x{a[1]}" for a in aix)
    exe = work / "bin" / tag
    if not exe.exists():
        exe.parent.mkdir(parents=True, exist_ok=True)
        src = exe.with_suffix(".cbl")
        src.write_text(KSUTIL.format(aix=aix_clause, lrecl=lrecl, layout=layout))
        run(["cobc", "-x", *COBC_FLAGS, "-o", str(exe), str(src)])
    return exe


def read_records(path: Path, lrecl):
    """Sample text files are line oriented (LF or CRLF): pad each line to LRECL."""
    if not path.exists() or path.stat().st_size == 0:
        return []
    data = path.read_bytes()
    out = []
    for line in data.split(b"\n"):
        if line.endswith(b"\r"):
            line = line[:-1]
        if not line:
            continue
        out.append(line[:lrecl].ljust(lrecl, b" "))
    return out


class Catalog:
    def __init__(self, job, work, job_dir):
        self.work, self.job_dir = work, job_dir
        self.defs = job["datasets"]
        self.data = work / "data"
        self.data.mkdir(parents=True, exist_ok=True)

    def path(self, name):
        return self.data / name

    def resolve_source(self, spec):
        if spec.startswith("seed:"):
            return APP / "data" / "ASCII" / spec[5:]
        if spec.startswith("golden:"):
            return GOLDEN_ROOT / spec[7:]
        return self.job_dir / spec

    def records(self, name):
        ds = self.defs[name]
        p = self.path(name)
        if ds["org"] == "raw":
            return [p.read_bytes()] if p.exists() else []
        if ds["org"] == "ksds":
            tmp = self.data / f"{name}.unload"
            env = dict(os.environ, DD_PSFILE=str(tmp), DD_KSFILE=str(p))
            run([str(ksutil(ds, self.work)), "UNLOAD"], env=env)
            raw = tmp.read_bytes()
        else:
            raw = p.read_bytes() if p.exists() else b""
        lrecl = ds["lrecl"]
        return [raw[i:i + lrecl] for i in range(0, len(raw), lrecl)]

    def store(self, name, records):
        ds = self.defs[name]
        p = self.path(name)
        for f in self.data.glob(name + "*"):
            f.unlink()
        if ds["org"] == "ksds":
            tmp = self.data / f"{name}.load"
            tmp.write_bytes(b"".join(records))
            env = dict(os.environ, DD_PSFILE=str(tmp), DD_KSFILE=str(p))
            if run([str(ksutil(ds, self.work)), "LOAD"], env=env, check=False) != 0:
                raise SystemExit(f"KSDS load failed for {name} (duplicate keys?)")
            tmp.unlink()
        else:
            p.write_bytes(b"".join(records))

    def initialise(self):
        for name, ds in self.defs.items():
            src = ds.get("input")
            recs = read_records(self.resolve_source(src), ds["lrecl"]) if src else []
            if src or ds["org"] == "ksds" or ds.get("init_empty"):
                self.store(name, recs)


# ---------------------------------------------------------------- steps
def step_sort(step, cat):
    sortin = step["sortin"] if isinstance(step["sortin"], list) else [step["sortin"]]
    recs = [r for name in sortin for r in cat.records(name)]
    for cond in step.get("include", []):
        start, length, op, value = cond
        value = value.encode("latin-1")
        ops = {"GE": lambda a, b: a >= b, "LE": lambda a, b: a <= b,
               "EQ": lambda a, b: a == b, "NE": lambda a, b: a != b,
               "GT": lambda a, b: a > b, "LT": lambda a, b: a < b}
        recs = [r for r in recs if ops[op](r[start - 1:start - 1 + length], value)]
    for start, length, order in reversed(step.get("fields", [])):
        recs.sort(key=lambda r: r[start - 1:start - 1 + length], reverse=(order == "D"))
    if step.get("outrec"):
        lrecl = cat.defs[step["sortout"]]["lrecl"]
        out = []
        for r in recs:
            buf = bytearray(b" " * lrecl)
            for outpos, inpos, length in step["outrec"]:
                buf[outpos - 1:outpos - 1 + length] = r[inpos - 1:inpos - 1 + length]
            out.append(bytes(buf))
        recs = out
    cat.store(step["sortout"], recs)
    return 0


def step_repro(step, cat):
    cat.store(step["outfile"], cat.records(step["infile"]))
    return 0


def step_program(step, cat, lib, work, env_base, sysout):
    env = dict(env_base)
    for dd, name in step.get("dd", {}).items():
        env[f"DD_{dd}"] = str(cat.path(name))
    if step.get("sysin") is not None:
        sysin = work / f"{step['name']}.sysin"
        sysin.write_text(step["sysin"])
        env["DD_SYSIN"] = str(sysin)
    cmd = [str(work / "jcldrv"), step["program"]]
    # Freeze the wall clock (incl. hundredths) so CURRENT-DATE is reproducible.
    ts = env["COB_CURRENT_DATE"].replace("/", "-")
    cmd = ["faketime", "-f", ts] + cmd
    if step.get("parm"):
        cmd.append(step["parm"])
    with open(sysout, "ab") as out:
        out.write(f"---- STEP {step['name']} PGM={step['program']}\n".encode())
        out.flush()
        stdin = open(env["DD_SYSIN"], "rb") if "DD_SYSIN" in env else subprocess.DEVNULL
        res = subprocess.run(cmd, env=env, stdout=out, stderr=subprocess.STDOUT,
                             stdin=stdin, cwd=work)
        out.write(f"---- RC={res.returncode}\n".encode())
    return res.returncode


def write_text_records(path, records):
    path.write_bytes(b"".join(r + b"\n" for r in records))


def execute(job_name, work):
    job_dir = GOLDEN_ROOT / job_name
    job = yaml.safe_load((job_dir / "job.yaml").read_text())
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    lib = compile_all(job, work)
    cat = Catalog(job, work, job_dir)
    cat.initialise()
    env = dict(os.environ)
    env.update({
        "COB_LIBRARY_PATH": str(lib),
        "COB_CURRENT_DATE": job["business_timestamp"],
        "COB_SET_TRACE": "N",
        "GOLDEN_NOWAIT": "Y",
        "TZ": "UTC",
        "LC_ALL": "C",
    })
    out = work / "out"
    out.mkdir()
    sysout = out / "SYSOUT.txt"
    rcs = []
    for step in job["steps"]:
        kind = step.get("kind", "program")
        log(f"step {step['name']} ({kind})")
        if kind == "sort":
            rc = step_sort(step, cat)
        elif kind == "repro":
            rc = step_repro(step, cat)
        else:
            rc = step_program(step, cat, lib, work, env, sysout)
        rcs.append(f"{step['name']} {rc}")
        if rc > step.get("max_rc", 4):
            log(f"step {step['name']} ended with RC={rc}; job stops (COND=(0,NE))")
            break
    (out / "RETURN-CODES.txt").write_text("\n".join(rcs) + "\n")
    for name in job["outputs"]:
        if cat.defs[name]["org"] == "raw":
            src = cat.path(name)
            (out / f"{name}.bin").write_bytes(src.read_bytes() if src.exists() else b"")
        else:
            write_text_records(out / f"{name}.dat", cat.records(name))
    return out, job_dir


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("job")
    ap.add_argument("--verify", action="store_true")
    ap.add_argument("--work", default=None)
    args = ap.parse_args()
    work = Path(args.work) if args.work else MODERN / "target" / "golden-work" / args.job
    out, job_dir = execute(args.job, work.resolve())
    expected = job_dir / "expected"
    if args.verify:
        if not expected.exists():
            raise SystemExit(f"no expected/ for {args.job}: capture first")
        cmp = filecmp.dircmp(out, expected)
        bad = cmp.diff_files + cmp.left_only + cmp.right_only
        if bad:
            for f in bad:
                log(f"DIFF {f}")
            subprocess.run(["diff", "-r", str(expected), str(out)])
            return 1
        log(f"{args.job}: zero diff against golden master ({len(cmp.same_files)} files)")
        return 0
    if expected.exists():
        shutil.rmtree(expected)
    shutil.copytree(out, expected)
    log(f"{args.job}: golden master captured in {expected.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
