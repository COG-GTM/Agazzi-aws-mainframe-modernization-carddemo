#!/usr/bin/env bash
# X1 golden masters: decodes the IMS unload IMSDATA.DBPAUTP0.dat (DBPAUTP0) and runs the original
# CBPAUP0C under GnuCOBOL + faketime with a DL/I stub (CBPAUP0J). --verify fails on any diff.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODERN="$(cd "$HERE/../../../.." && pwd)"
REPO="$(cd "$MODERN/.." && pwd)"
LEGACY="$REPO/app/app-authorization-ims-db2-mq"
GOLDEN="$MODERN/carddemo-parity/src/test/resources/golden/x1-authorization"
WORK="$MODERN/carddemo-ext-authorization/target/x1-golden"
VERIFY="${1:-}"
command -v cobc >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq gnucobol; }
command -v faketime >/dev/null || sudo apt-get install -y -qq faketime
rm -rf "$WORK" && mkdir -p "$WORK/out"
export TZ=UTC LC_ALL=C

python3 "$HERE/imsdecode.py" "$LEGACY/data/EBCDIC/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat" \
  "$WORK/out/DBPAUTP0/expected" >/dev/null

python3 "$HERE/dlipre.py" "$LEGACY/cbl/CBPAUP0C.cbl" "$WORK/CBPAUP0C.cbl"
cobc -x -std=ibm -I "$LEGACY/cpy" -o "$WORK/CBPAUP0C" "$WORK/CBPAUP0C.cbl" "$HERE/dlistub.c"

while IFS='|' read -r scen date sysin; do
  [ -z "$scen" ] && continue
  d="$WORK/out/CBPAUP0J/$scen"
  mkdir -p "$d/expected"
  printf '%s\n' "$sysin" > "$d/SYSIN.txt"
  printf '%s\n' "$date" > "$d/BUSINESS-DATE.txt"
  rc=0
  X1_ROOT_IN="$WORK/out/DBPAUTP0/expected/PAUTSUM0.bin" \
  X1_CHILD_IN="$WORK/out/DBPAUTP0/expected/PAUTDTL1.bin" \
  X1_ROOT_OUT="$d/expected/PAUTSUM0.bin" X1_CHILD_OUT="$d/expected/PAUTDTL1.bin" \
  COB_CURRENT_DATE="${date//-//} 12:00:00" faketime "$date 12:00:00" \
    "$WORK/CBPAUP0C" < "$d/SYSIN.txt" > "$d/expected/SYSOUT.txt" || rc=$?
  echo "CBPAUP0C $rc" > "$d/expected/RETURN-CODES.txt"
done < "$HERE/cbpaup0j-scenarios.txt"

if [ "$VERIFY" = "--verify" ]; then
  diff -r "$WORK/out" "$GOLDEN" -x online -x requests -x README.md && echo "[x1-golden] verify OK"
else
  mkdir -p "$GOLDEN" && cp -r "$WORK/out/." "$GOLDEN/" && echo "[x1-golden] written to $GOLDEN"
fi
