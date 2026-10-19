#!/usr/bin/env bash
# b1-posting golden masters: the Phase-0 harness (tools/golden/golden.py) compiled with
# -fsign=EBCDIC, so that the z/OS overpunch signs of app/data/ASCII ({ A-I = +0..9, } J-R =
# -0..9) are read as on the mainframe. Without it GnuCOBOL 3.1.2 (-std=ibm, ASCII) reads every
# overpunch character as digit 0 positive (e.g. 0000000102N = -10.25 becomes +10.20).
# Usage: run-cobol.sh <JOB>|all [--verify]   (JOB = directory under golden/b1-posting)
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../../../../../tools/golden"
JOBS=(POSTTRAN POSTTRAN-EDGE POSTTRAN-EMPTY CBTRN01C TRANBKP)
run() {
  python3 - "../b1-posting/$1" "${@:2}" <<PY
import sys
sys.path.insert(0, "$TOOLS")
import golden
golden.COBC_FLAGS.insert(1, "-fsign=EBCDIC")
sys.argv = ["golden.py"] + sys.argv[1:]
sys.exit(golden.main())
PY
}
if [[ "${1:-}" == "all" ]]; then
  for j in "${JOBS[@]}"; do run "$j" "${@:2}"; done
else
  run "$@"
fi
