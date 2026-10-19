#!/usr/bin/env python3
"""Fails if any file under app/ is owned by zero or more than one workstream (modern/ownership.yaml)."""
import fnmatch
import re
import sys
from pathlib import Path

import yaml

MODERN = Path(__file__).resolve().parents[2]
REPO = MODERN.parent
IGNORED = {".gitkeep", ".DS_Store"}


def expand_braces(pattern):
    m = re.search(r"\{([^}]*)\}", pattern)
    if not m:
        return [pattern]
    return [x for alt in m.group(1).split(",")
            for x in expand_braces(pattern[:m.start()] + alt + pattern[m.end():])]


def matches(path, pattern):
    """Segment-wise glob: `*`/`?`/`[..]` never cross `/`; a trailing `/**` matches any depth."""
    for p in expand_braces(pattern):
        if p.endswith("/**"):
            if path.startswith(p[:-2]):
                return True
            continue
        ps, fs = p.split("/"), path.split("/")
        if len(ps) == len(fs) and all(fnmatch.fnmatchcase(f, g) for f, g in zip(fs, ps)):
            return True
    return False


def owners_by_file():
    cfg = yaml.safe_load((MODERN / "ownership.yaml").read_text())
    files = sorted(p.relative_to(REPO).as_posix() for p in (REPO / "app").rglob("*")
                   if p.is_file() and p.name not in IGNORED)
    result = {f: [] for f in files}
    unused = []
    for ws, spec in cfg["workstreams"].items():
        for pattern in spec.get("legacy") or []:
            hit = [f for f in files if matches(f, pattern)]
            if not hit:
                unused.append(f"{ws}: {pattern}")
            for f in hit:
                result[f].append(ws)
    return result, unused


def main():
    result, unused = owners_by_file()
    orphan = [f for f, o in result.items() if not o]
    dup = {f: o for f, o in result.items() if len(o) > 1}
    for f in orphan:
        print(f"UNASSIGNED  {f}")
    for f, o in dup.items():
        print(f"DUPLICATE   {f}: {', '.join(o)}")
    for u in unused:
        print(f"NO-MATCH    {u}")
    if orphan or dup or unused:
        return 1
    print(f"ownership OK: {len(result)} artifacts under app/, each owned by exactly one workstream")
    return 0


if __name__ == "__main__":
    sys.exit(main())
