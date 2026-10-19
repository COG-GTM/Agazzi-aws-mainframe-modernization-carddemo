#!/usr/bin/env python3
"""Regenerates the CardDemo legacy inventory (plan §0.1) from app/.

Outputs (modern/docs/inventory/): inventory.json, call-graph.mmd and *.md.
  inventory.py            regenerate
  inventory.py --check    regenerate in memory and fail if the committed files differ
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

MODERN = Path(__file__).resolve().parents[2]
REPO = MODERN.parent
APP = REPO / "app"
OUT = MODERN / "docs" / "inventory"


def rel(p):
    return p.relative_to(REPO).as_posix()


def read(p):
    return p.read_text(encoding="latin-1", errors="replace")


def code_lines(text):
    """COBOL fixed format: drop comment lines (col 7 = '*' or '/'), sequence area."""
    out = []
    for line in text.splitlines():
        if len(line) > 6 and line[6] in "*/":
            continue
        out.append(line[:72])
    return out


def files(pattern_dirs, exts):
    res = []
    for d in pattern_dirs:
        for p in sorted(APP.glob(d)):
            if p.is_file() and p.suffix.lower() in exts:
                res.append(p)
    return res


CICS_OPS = ["READNEXT", "READPREV", "STARTBR", "ENDBR", "READ", "WRITEQ", "READQ", "DELETEQ",
            "REWRITE", "WRITE", "DELETE", "SEND", "RECEIVE", "RETURN", "XCTL", "LINK",
            "SYNCPOINT", "ASKTIME", "FORMATTIME", "HANDLE", "ASSIGN", "INQUIRE", "START",
            "RETRIEVE", "ABEND", "GETMAIN"]


def scan_program(p):
    text = read(p)
    lines = code_lines(text)
    body = "\n".join(lines)
    loc = sum(1 for line in lines if line.strip())
    copies = sorted(set(re.findall(r"\bCOPY\s+'?([A-Z0-9#@$-]+)'?", body, re.I)))
    copies = [c.upper() for c in copies if c.upper() not in ("DFHAID", "DFHBMSCA")] + \
        [c.upper() for c in copies if c.upper() in ("DFHAID", "DFHBMSCA")]
    calls = sorted(set(m.upper() for m in re.findall(r"\bCALL\s+'([A-Z0-9$#@]+)'", body, re.I)))
    cics_blocks = re.findall(r"EXEC\s+CICS(.*?)END-EXEC", body, re.S | re.I)
    ops, datasets, xctl, link = defaultdict(int), set(), set(), set()
    literals = dict(re.findall(r"\b([A-Z0-9-]+)\s+PIC\s+X\(\d+\)\s+VALUE\s+'([A-Z0-9]{4,8})'", body))

    def resolve(tok):
        tok = tok.strip().strip("'")
        return literals.get(tok, tok)

    for blk in cics_blocks:
        words = blk.split()
        if not words:
            continue
        op = words[0].upper()
        if op in ("SEND", "RECEIVE") and len(words) > 1:
            op = f"{op} {words[1].split('(')[0].upper()}"
        ops[op] += 1
        for m in re.findall(r"(?:DATASET|FILE)\s*\(\s*([^)]+)\)", blk, re.I):
            datasets.add(resolve(m))
        for m in re.findall(r"PROGRAM\s*\(\s*([^)]+)\)", blk, re.I):
            (xctl if op == "XCTL" else link if op == "LINK" else set()).add(resolve(m))
    xctl |= {v for k, v in literals.items() if re.fullmatch(r"CO[A-Z0-9]{5}C", v) and
             v != p.stem.upper() and re.search(r"(PGM|PROGRAM|NEXT|BACK|MENU|SIGN)", k)}
    online = bool(cics_blocks)
    sub = (not online) and bool(re.search(r"PROCEDURE\s+DIVISION\s+USING", body, re.I)) and \
        not re.search(r"\bPARM|EXTERNAL-PARMS", body)
    kind = "online" if online else ("subroutine" if sub else "batch")
    if re.search(r"EXEC\s+DLI|CBLTDLI", body, re.I):
        kind += "+ims"
    if re.search(r"EXEC\s+SQL", body, re.I):
        kind += "+db2"
    if re.search(r"MQOPEN|MQGET|MQPUT", body, re.I):
        kind += "+mq"
    return {"name": p.stem.upper(), "path": rel(p), "loc": loc, "kind": kind,
            "copybooks": copies, "calls": calls, "xctl": sorted(xctl), "link": sorted(link),
            "cics_ops": dict(sorted(ops.items())), "cics_files": sorted(datasets)}


def scan_csd(p):
    text = read(p)
    res = defaultdict(list)
    for m in re.finditer(r"DEFINE\s+(\w+)\((\S+?)\)\s+GROUP\((\S+?)\)(.*?)(?=DEFINE\s|\Z)", text, re.S):
        kind, name, group, rest = m.groups()
        item = {"name": name, "group": group, "source": rel(p)}
        for attr in ("PROGRAM", "DSNAME", "TRANSACTION"):
            a = re.search(rf"\b{attr}\(([^)]+)\)", rest)
            if a:
                item[attr.lower()] = a.group(1)
        res[kind.lower()].append(item)
    return res


def scan_bms(p):
    text = read(p)
    stmts, cur = [], ""
    for line in text.splitlines():
        if line.startswith("*"):
            continue
        body = line[:71].rstrip()
        cur += body.strip() if cur else body
        if len(line) > 71 and line[71] not in " ":  # assembler continuation in column 72
            continue
        stmts.append(cur)
        cur = ""
    joined = "\n".join(stmts + ([cur] if cur else []))
    maps, fields = [], []
    for m in re.finditer(r"^(\S+)\s+DFHMDI\s+(.*)$", joined, re.M):
        maps.append(m.group(1))
    for m in re.finditer(r"^(\S*)\s+DFHMDF\s+(.*)$", joined, re.M):
        name, attrs = m.groups()
        if not name:
            continue
        pos = re.search(r"POS=\((\d+),(\d+)\)", attrs)
        length = re.search(r"LENGTH=(\d+)", attrs)
        attrb = re.search(r"ATTRB=\(?([A-Z,]+)\)?", attrs)
        fields.append({"name": name, "row": int(pos.group(1)) if pos else None,
                       "col": int(pos.group(2)) if pos else None,
                       "length": int(length.group(1)) if length else None,
                       "attrb": attrb.group(1).split(",") if attrb else [],
                       "input": bool(attrb and "UNPROT" in attrb.group(1))})
    mapset = re.search(r"^(\S+)\s+DFHMSD", joined, re.M)
    return {"mapset": mapset.group(1) if mapset else p.stem.upper(), "maps": maps,
            "path": rel(p), "fields": fields}


def scan_jcl(p):
    text = read(p)
    steps, cur = [], None
    lines = [line[:72] for line in text.splitlines()]
    instream = False
    for i, line in enumerate(lines):
        if line.startswith("//*"):
            continue
        if line.startswith("//"):
            instream = False
            m = re.match(r"//(\S+)\s+EXEC\s+(?:PGM=([^,\s]+)|PROC=([^,\s]+)|([^,\s=]+))(.*)", line)
            if m:
                parm = re.search(r"PARM='([^']*)'", m.group(5) or "")
                cur = {"step": m.group(1), "pgm": m.group(2), "proc": m.group(3) or m.group(4),
                       "parm": parm.group(1) if parm else None, "dd": []}
                steps.append(cur)
                continue
            dd = re.match(r"//(\S+)\s+DD\s*(.*)", line)
            if dd and cur is not None:
                cur["dd"].append({"dd": dd.group(1), "dsn": None, "disp": None})
                if "*" in dd.group(2).split(",")[0] or "DATA" in dd.group(2)[:5]:
                    instream = True
            if cur and cur["dd"]:
                dsn = re.search(r"DSN=([^,\s]+)", line)
                disp = re.search(r"DISP=(\([^)]*\)|[A-Z]+)", line)
                if dsn and not cur["dd"][-1]["dsn"]:
                    cur["dd"][-1]["dsn"] = dsn.group(1)
                if disp and not cur["dd"][-1]["disp"]:
                    cur["dd"][-1]["disp"] = disp.group(1)
    body = "\n".join(line for line in lines if not line.startswith("//*"))
    clusters = []
    for m in re.finditer(r"DEFINE\s+(CLUSTER|ALTERNATEINDEX|AIX|PATH|GENERATIONDATAGROUP|GDG)\s*-?"
                         r"(.*?)(?=DEFINE\s|\n//|\Z)", body, re.S):
        kind, rest = m.group(1), m.group(2)
        g = lambda k: (re.search(rf"\b{k}\s*\(\s*([^)]*?)\s*\)", rest) or [None, None])[1]
        clusters.append({"type": {"AIX": "ALTERNATEINDEX", "GDG": "GENERATIONDATAGROUP"}.get(kind, kind),
                         "name": g("NAME"), "keys": g("KEYS"), "recordsize": g("RECORDSIZE"),
                         "relate": g("RELATE") or g("PATHENTRY"), "limit": g("LIMIT"),
                         "org": "NONINDEXED" if "NONINDEXED" in rest else "NUMBERED"
                         if "NUMBERED" in rest else "INDEXED" if kind == "CLUSTER" else None})
    return {"job": p.stem.upper(), "path": rel(p), "steps": steps, "defines": clusters}


def scan_controlm(p):
    root = ET.parse(p).getroot()
    flows = []
    for folder in root.iter("FOLDER"):
        jobs = []
        for job in folder.iter("JOB"):
            jobs.append({"job": job.get("JOBNAME"), "member": job.get("MEMNAME"),
                         "in": [c.get("NAME") for c in job.iter("INCOND")],
                         "out": [c.get("NAME") for c in job.iter("OUTCOND") if c.get("SIGN") == "+"]})
        flows.append({"folder": folder.get("FOLDER_NAME"), "jobs": jobs})
    return flows


def build():
    inv = {"generated_from": "app/", "programs": [], "transactions": [], "csd": {}, "bms": [],
           "jcl": [], "procs": [], "copybooks": [], "scheduler": {}, "other": []}
    for p in files(["cbl/*", "app-*/cbl/*"], {".cbl"}):
        inv["programs"].append(scan_program(p))
    for p in files(["csd/*", "app-*/csd/*"], {".csd"}):
        for k, v in scan_csd(p).items():
            inv["csd"].setdefault(k, []).extend(v)
    inv["transactions"] = sorted(inv["csd"].get("transaction", []), key=lambda t: t["name"])
    for p in files(["bms/*", "app-*/bms/*"], {".bms"}):
        inv["bms"].append(scan_bms(p))
    for p in files(["jcl/*", "app-*/jcl/*"], {".jcl"}):
        inv["jcl"].append(scan_jcl(p))
    for p in files(["proc/*"], {".prc"}):
        inv["procs"].append(scan_jcl(p))
    consumers = defaultdict(list)
    for prog in inv["programs"]:
        for c in prog["copybooks"]:
            consumers[c].append(prog["name"])
    for p in files(["cpy/*", "cpy-bms/*", "app-*/cpy/*", "app-*/cpy-bms/*", "app-*/dcl/*"],
                   {".cpy", ".dcl"}):
        name = p.stem.upper()
        inv["copybooks"].append({"name": name, "path": rel(p), "loc": len(read(p).splitlines()),
                                 "has_tabs": "\t" in read(p), "consumers": sorted(set(consumers[name]))})
    inv["scheduler"]["controlm"] = scan_controlm(APP / "scheduler" / "CardDemo.controlm")
    inv["scheduler"]["ca7_jobs"] = sorted(set(re.findall(r"LJOB,JOB=(\w+)", read(APP / "scheduler" / "CardDemo.ca7"))))
    known = {x["path"] for k in ("programs", "bms", "jcl", "procs", "copybooks") for x in inv[k]}
    known |= {x["source"] for x in inv["csd"].get("transaction", [])}
    for p in sorted(APP.rglob("*")):
        if p.is_file() and rel(p) not in known and not p.suffix.lower() == ".csd":
            inv["other"].append({"path": rel(p), "kind": p.parent.name})
    datasets = {}
    for j in inv["jcl"]:
        for d in j["defines"]:
            if d["name"]:
                datasets.setdefault(d["name"], {**d, "defined_in": j["job"]})
    inv["datasets"] = sorted(datasets.values(), key=lambda d: d["name"])
    progs = inv["programs"]
    inv["totals"] = {"programs": len(progs), "online": sum(p["kind"].startswith("online") for p in progs),
                     "batch": sum(p["kind"].startswith("batch") for p in progs),
                     "subroutines": sum(p["kind"].startswith("subroutine") for p in progs),
                     "cobol_loc": sum(p["loc"] for p in progs), "bms_mapsets": len(inv["bms"]),
                     "jcl": len(inv["jcl"]), "transactions": len(inv["transactions"]),
                     "copybooks": len(inv["copybooks"])}
    return inv


def md_table(header, rows):
    out = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    out += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
    return "\n".join(out) + "\n"


def render(inv):
    out = {"inventory.json": json.dumps(inv, indent=2, sort_keys=True) + "\n"}
    hdr = "<!-- generated by modern/tools/inventory/inventory.py — do not edit -->\n"
    t = inv["totals"]
    out["README.md"] = hdr + "# Inventario legacy CardDemo\n\n" + md_table(
        ["Metric", "Value"], sorted(t.items())) + "\nFiles: programs.md, transactions.md, bms.md, jcl.md, " \
        "datasets.md, copybooks.md, scheduler.md, call-graph.mmd, inventory.json\n"
    out["programs.md"] = hdr + "# Programmi COBOL\n\n" + md_table(
        ["Program", "Kind", "LOC", "Path", "CALL", "XCTL/LINK", "CICS files", "CICS ops"],
        [(p["name"], p["kind"], p["loc"], p["path"], " ".join(p["calls"]),
          " ".join(p["xctl"] + p["link"]), " ".join(p["cics_files"]),
          " ".join(f"{k}×{v}" for k, v in p["cics_ops"].items())) for p in inv["programs"]])
    out["transactions.md"] = hdr + "# Transazioni CICS (CSD)\n\n" + md_table(
        ["TransID", "Program", "Group", "CSD"],
        [(x["name"], x.get("program", ""), x["group"], x["source"]) for x in inv["transactions"]]) + \
        "\n## File CICS\n\n" + md_table(["File", "DSN", "Group"], [(x["name"], x.get("dsname", ""), x["group"])
                                                                  for x in inv["csd"].get("file", [])])
    out["bms.md"] = hdr + "# Mappe BMS\n\n" + "\n".join(
        f"## {b['mapset']} ({b['path']})\n\n" + md_table(
            ["Field", "Row", "Col", "Len", "Input", "ATTRB"],
            [(f["name"], f["row"], f["col"], f["length"], "Y" if f["input"] else "", ",".join(f["attrb"]))
             for f in b["fields"]]) for b in inv["bms"])
    out["jcl.md"] = hdr + "# JCL e PROC\n\n" + "\n".join(
        f"## {j['job']} ({j['path']})\n\n" + md_table(
            ["Step", "PGM/PROC", "PARM", "DD", "DSN", "DISP"],
            [(s["step"], s["pgm"] or s["proc"], s["parm"] or "", d["dd"], d["dsn"] or "", d["disp"] or "")
             for s in j["steps"] for d in (s["dd"] or [{"dd": "", "dsn": "", "disp": ""}])])
        for j in inv["jcl"] + inv["procs"])
    out["datasets.md"] = hdr + "# DEFINE CLUSTER / AIX / PATH / GDG\n\n" + md_table(
        ["Name", "Type", "Org", "KEYS(len off)", "RECORDSIZE", "RELATE", "LIMIT", "Defined in"],
        [(d["name"], d["type"], d["org"] or "", d["keys"] or "", d["recordsize"] or "", d["relate"] or "",
          d["limit"] or "", d["defined_in"]) for d in inv["datasets"]])
    out["copybooks.md"] = hdr + "# Copybook → consumatori\n\n" + md_table(
        ["Copybook", "Path", "Lines", "TAB", "Consumers"],
        [(c["name"], c["path"], c["loc"], "⚠" if c["has_tabs"] else "", " ".join(c["consumers"]))
         for c in inv["copybooks"]])
    sch = "".join(f"## {f['folder']}\n\n" + md_table(["Job", "IN", "OUT"], [
        (j["job"], " ".join(j["in"]), " ".join(j["out"])) for j in f["jobs"]]) + "\n"
        for f in inv["scheduler"]["controlm"])
    out["scheduler.md"] = hdr + "# Scheduler\n\n" + sch + "## CA-7 jobs\n\n" + \
        ", ".join(inv["scheduler"]["ca7_jobs"]) + "\n"
    g = ["%% generated by modern/tools/inventory/inventory.py", "graph LR"]
    for t_ in inv["transactions"]:
        if t_.get("program"):
            g.append(f"  T_{t_['name']}([{t_['name']}]) --> {t_['program']}")
    for p in inv["programs"]:
        for x in p["xctl"]:
            g.append(f"  {p['name']} -- XCTL --> {x}")
        for x in p["link"] + p["calls"]:
            g.append(f"  {p['name']} -- CALL --> {x}")
    for j in inv["jcl"]:
        for s in j["steps"]:
            if s["pgm"] and re.match(r"^C[BO]", s["pgm"]):
                g.append(f"  J_{j['job']}[[{j['job']}]] --> {s['pgm']}")
    out["call-graph.mmd"] = "\n".join(dict.fromkeys(g)) + "\n"
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    rendered = render(build())
    if args.check:
        stale = [n for n, c in rendered.items() if not (OUT / n).exists() or (OUT / n).read_text() != c]
        if stale:
            print("inventory out of date, run modern/tools/inventory/inventory.py:", ", ".join(stale))
            return 1
        print(f"inventory up to date ({len(rendered)} files)")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    for n, c in rendered.items():
        (OUT / n).write_text(c)
    print(f"wrote {len(rendered)} files to {OUT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
