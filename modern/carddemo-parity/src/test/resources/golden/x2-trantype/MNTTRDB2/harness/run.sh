#!/usr/bin/env bash
# Runs the original COBTUPDT (MNTTRDB2) under GnuCOBOL 3.1.2 with the SQL stub and regenerates
# SYSOUT, RC and TRANTYPE.after (TRANEXTR 60-byte layout) next to INPFILE. --verify compares.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
gold="$(dirname "$here")"
repo="$(cd "$gold/../../../../../../../.." && pwd)"
seed="$repo/modern/carddemo-domain/src/main/resources/db/seed/V2__seed_sample_data.sql"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
python3 "$here/prepare.py" "$repo/app/app-transaction-type-db2/cbl/COBTUPDT.cbl" "$work/COBTUPDT.cbl"
cobc -x -o "$work/cobtupdt" "$work/COBTUPDT.cbl" "$here/sqlstub.c"
grep -E "^\('[0-9]{2}', '" "$seed" | sed -E "s/^\('([0-9]{2})', '([^']*)'.*/\1\2/" > "$work/TRANTYPE.tbl"
grep -E "^\('[0-9]{2}', [0-9]+, '" "$seed" | cut -c3-4 > "$work/TRANCATG.tbl"
set +e
INPFILE="$gold/INPFILE" TRANTYPE_TBL="$work/TRANTYPE.tbl" TRANCATG_TBL="$work/TRANCATG.tbl" \
  "$work/cobtupdt" > "$work/SYSOUT"
rc=$?
set -e
echo "$rc" > "$work/RC"
sort "$work/TRANTYPE.tbl" | awk '{ printf "%-52s00000000\n", $0 }' > "$work/TRANTYPE.after"
if [[ "${1:-}" == "--verify" ]]; then
  for f in SYSOUT RC TRANTYPE.after; do diff -u "$gold/$f" "$work/$f"; done
  echo "MNTTRDB2 golden verified"
else
  cp "$work/SYSOUT" "$work/RC" "$work/TRANTYPE.after" "$gold/"
  echo "MNTTRDB2 golden regenerated (rc=$rc)"
fi
