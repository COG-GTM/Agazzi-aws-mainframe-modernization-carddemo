#!/usr/bin/env bash
# o1-auth MENUOPTS golden: runs option parsing / menu STRING paragraphs copied verbatim from the
# original COMEN01C/COADM01C (extract.py) under GnuCOBOL and writes expected/MENUOPTS.txt.
# Usage: run.sh [--verify]   (--verify fails on any difference with the committed golden)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../../../../../.." && pwd)"
WORK="$REPO/modern/carddemo-online-auth/target/golden-work/MENUOPTS"
command -v cobc >/dev/null || { sudo apt-get update -qq && sudo apt-get install -y -qq gnucobol; }
rm -rf "$WORK" && mkdir -p "$WORK"
python3 "$HERE/extract.py" > "$WORK/MENUDRV.cbl"
cobc -x -std=ibm -I "$REPO/app/cpy" -o "$WORK/menudrv" "$WORK/MENUDRV.cbl"
python3 - "$HERE/input/OPTIONS.hex" "$WORK/OPTIONS.bin" <<'PY'
import sys
lines = [l.strip() for l in open(sys.argv[1]) if l.strip() and not l.startswith("#")]
open(sys.argv[2], "wb").write(b"".join(bytes.fromhex(l) for l in lines))
PY
(cd "$WORK" && LC_ALL=C TZ=UTC ./menudrv)
python3 - "$WORK/MENUOPTS.out" "$WORK/MENUOPTS.txt" <<'PY'
import sys
out = []
for raw in open(sys.argv[1], "rb").read().split(b"\n"):
    if not raw:
        continue
    if raw.startswith(b"O|"):
        # O|case|WS-OPTION-X|numeric|WS-OPTION|OPTIONO|valid-user|valid-admin (bytes as hex)
        f = raw.rstrip(b" ").split(b"|")
        out.append("|".join([f[0].decode(), f[1].decode(), f[2].hex(), f[3].decode(),
                             f[4].hex(), f[5].hex(), f[6].decode(), f[7].decode()]))
    else:
        out.append(raw.rstrip(b" ").decode("ascii"))
open(sys.argv[2], "w").write("\n".join(out) + "\n")
PY
if [ "${1:-}" = "--verify" ]; then
  diff -u "$HERE/expected/MENUOPTS.txt" "$WORK/MENUOPTS.txt"
  echo "[golden] MENUOPTS: zero diff"
else
  cp "$WORK/MENUOPTS.txt" "$HERE/expected/MENUOPTS.txt"
  echo "[golden] MENUOPTS: expected/MENUOPTS.txt written"
fi
