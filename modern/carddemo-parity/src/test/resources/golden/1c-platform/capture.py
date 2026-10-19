#!/usr/bin/env python3
"""Capture/verify the 1c-platform golden masters with the unchanged tools/golden/golden.py."""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODERN = HERE.parents[5]
sys.path.insert(0, str(MODERN / "tools" / "golden"))
import golden  # noqa: E402

if len(sys.argv) < 2:
    raise SystemExit("usage: capture.py <JOB> [--verify]")
job = sys.argv[1]
_find_source = golden.find_source


def find_source(program):
    local = HERE / job / "src" / f"{program}.cbl"
    return local if local.exists() else _find_source(program)


golden.find_source = find_source
work = MODERN / "target" / "golden-work" / "1c-platform" / job
sys.argv = ["golden.py", f"../1c-platform/{job}", "--work", str(work), *sys.argv[2:]]
sys.exit(golden.main())
