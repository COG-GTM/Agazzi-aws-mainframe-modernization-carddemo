"""B4 golden-master harness: modern/tools/golden/golden.py with the B4 golden root.

Usage: golden_b4.py <JOB> [--verify]   (JOB = directory under golden/b4-datalifecycle)

Harness-only adaptations (app/ is never modified, the shared harness is reused as-is):
- CBEXPORT/CBIMPORT declare RECORD KEY IS EXPORT-SEQUENCE-NUM, a WORKING-STORAGE item that
  GnuCOBOL rejects as key of the FD. The FD record is redefined with an X(4) overlay at the
  same offset (27) so the key keeps the original big-endian COMP bytes.
- Signed zoned fields declared in job.yaml (`zoned_fields: [[offset, length], ...]`) are
  converted from the EBCDIC overpunch of the sample data ({, A-I, }, J-R) to the GnuCOBOL
  ASCII sign convention (plain digit / p-y): GnuCOBOL otherwise reads them as +0 and the
  golden would not reflect mainframe semantics.
- Fixed-length binary goldens (records + LF, as written by golden.py) are re-read by length,
  since COMP bytes may contain 0x0A.
"""
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODERN = HERE.parent.parent
sys.path.insert(0, str(MODERN / "tools" / "golden"))
import golden  # noqa: E402

golden.GOLDEN_ROOT = (
    MODERN / "carddemo-parity" / "src" / "test" / "resources" / "golden" / "b4-datalifecycle")

_adapt_source = golden.adapt_source
_read_records = golden.read_records


def adapt_source(src: Path, out: Path):
    if src.stem.upper() not in ("CBEXPORT", "CBIMPORT"):
        return _adapt_source(src, out)
    text = src.read_text(encoding="latin-1")
    text, n = re.subn(
        r"01\s+(EXPORT-(?:OUTPUT|INPUT)-RECORD)\s+PIC X\(500\)\.",
        r"01  \1.\n           05 FILLER PIC X(27).\n"
        r"           05 EXP-FD-SEQ PIC X(4).\n           05 FILLER PIC X(469).",
        text)
    text = text.replace("RECORD KEY IS EXPORT-SEQUENCE-NUM", "RECORD KEY IS EXP-FD-SEQ")
    if n != 1:
        raise SystemExit(f"{src.name}: export FD record not found")
    out.write_text(text, encoding="latin-1")


def read_records(path: Path, lrecl):
    data = path.read_bytes() if path.exists() else b""
    step = lrecl + 1
    if data and len(data) % step == 0 and all(
            data[i + lrecl] == 0x0A for i in range(0, len(data), step)):
        return [data[i:i + lrecl] for i in range(0, len(data), step)]
    return _read_records(path, lrecl)


_OVERPUNCH = {ord("{"): ord("0"), ord("}"): ord("p")}
_OVERPUNCH.update({ord("A") + i: ord("1") + i for i in range(9)})
_OVERPUNCH.update({ord("J") + i: ord("q") + i for i in range(9)})


def normalise(record, zoned_fields):
    buf = bytearray(record)
    for off, length in zoned_fields:
        last = off + length - 1
        buf[last] = _OVERPUNCH.get(buf[last], buf[last])
    return bytes(buf)


def initialise(self):
    for name, ds in self.defs.items():
        src = ds.get("input")
        recs = golden.read_records(self.resolve_source(src), ds["lrecl"]) if src else []
        recs = [normalise(r, ds.get("zoned_fields", [])) for r in recs]
        if src or ds["org"] == "ksds" or ds.get("init_empty"):
            self.store(name, recs)


golden.Catalog.initialise = initialise
golden.adapt_source = adapt_source
golden.read_records = read_records

if __name__ == "__main__":
    sys.exit(golden.main())
