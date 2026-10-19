#!/usr/bin/env bash
# Runs an original CardDemo COBOL batch job under GnuCOBOL and captures (or
# verifies with --verify) its golden master. See modern/tools/golden/README.md.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if ! command -v cobc >/dev/null 2>&1; then
  echo "[golden] GnuCOBOL not found: installing (sudo apt-get install -y gnucobol)" >&2
  sudo apt-get update -qq && sudo apt-get install -y -qq gnucobol
fi
if ! command -v faketime >/dev/null 2>&1; then
  sudo apt-get install -y -qq faketime
fi
python3 -c 'import yaml' 2>/dev/null || python3 -m pip install -q pyyaml
if [ "$#" -lt 1 ]; then
  echo "usage: run-cobol.sh <JOB|all> [--verify]" >&2; exit 2
fi
JOB="$1"; shift
if [ "$JOB" = "all" ]; then
  ROOT="$HERE/../../carddemo-parity/src/test/resources/golden/batch"
  for d in $(cat "$ROOT/ORDER"); do python3 "$HERE/golden.py" "$d" "$@"; done
else
  exec python3 "$HERE/golden.py" "$JOB" "$@"
fi
