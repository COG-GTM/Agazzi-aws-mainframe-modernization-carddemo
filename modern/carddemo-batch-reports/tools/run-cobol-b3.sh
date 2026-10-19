#!/usr/bin/env bash
# Golden masters of b3-reports scenarios (golden/b3-reports/<SCENARIO>/job.yaml), produced by the
# shared GnuCOBOL harness (tools/golden/golden.py) with its root pointed at golden/b3-reports.
# "golden:../batch/<JOB>/..." inputs reuse the Fase 0 masters.
# usage: run-cobol-b3.sh <SCENARIO|all> [--verify]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODERN="$(cd "$HERE/../.." && pwd)"
ROOT="$MODERN/carddemo-parity/src/test/resources/golden/b3-reports"
run() {
  python3 - "$MODERN/tools/golden/golden.py" "$ROOT" "$@" <<'PY'
import importlib.util, sys
from pathlib import Path
spec = importlib.util.spec_from_file_location("golden", sys.argv[1])
g = importlib.util.module_from_spec(spec)
spec.loader.exec_module(g)
g.GOLDEN_ROOT = Path(sys.argv[2])
sys.argv = ["golden.py"] + sys.argv[3:]
sys.exit(g.main())
PY
}
JOB="$1"; shift
if [ "$JOB" = "all" ]; then
  for d in $(cat "$ROOT/ORDER"); do run "$d" "$@"; done
else
  run "$JOB" "$@"
fi
