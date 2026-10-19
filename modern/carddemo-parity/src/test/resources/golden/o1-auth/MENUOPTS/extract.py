#!/usr/bin/env python3
"""Builds MENUDRV.cbl: a harness that runs paragraphs copied verbatim from the original
app/cbl/COMEN01C.cbl and app/cbl/COADM01C.cbl (option parsing of PROCESS-ENTER-KEY, the STRING
statements of BUILD-MENU-OPTIONS and of the 'not installed'/'coming soon' messages) without CICS."""
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[8]
COMEN = (REPO / "app/cbl/COMEN01C.cbl").read_text().splitlines()
COADM = (REPO / "app/cbl/COADM01C.cbl").read_text().splitlines()


def body(line):
    return line[6:72].rstrip() if len(line) > 6 else ""


def block(src, start, end, nth=1, repl=()):
    """Lines from the nth line containing `start` up to the first following line with `end`."""
    hits = [i for i, l in enumerate(src) if start in body(l)]
    i = hits[nth - 1]
    j = next(k for k in range(i, len(src)) if end in body(src[k]))
    out = []
    for l in src[i:j + 1]:
        b = body(l)
        if b.startswith("*") or (len(l) > 6 and l[6] == "*"):
            continue
        for a, c in repl:
            b = b.replace(a, c)
        out.append("      " + b)
    return "\n".join(out)


PARSE = block(COMEN, "PERFORM VARYING WS-IDX", "MOVE WS-OPTION ", 1,
              [("OPTIONI OF COMEN1AI", "OPTIONI"), ("OPTIONO OF COMEN1AO", "OPTIONO")])
MENU_STRING = block(COMEN, "STRING CDEMO-MENU-OPT-NUM(WS-IDX)", "INTO WS-MENU-OPT-TXT")
ADMIN_STRING = block(COADM, "STRING CDEMO-ADMIN-OPT-NUM(WS-IDX)", "INTO WS-ADMIN-OPT-TXT")
NOT_INSTALLED = block(COMEN, "STRING 'This option '", "INTO WS-MESSAGE", 1)
COMING_SOON = block(COMEN, "STRING 'This option '", "INTO WS-MESSAGE", 2)
ADMIN_NOT_INSTALLED = block(COADM, "STRING 'This option '", "INTO WS-MESSAGE", 2)

TEMPLATE = Path(__file__).with_name("MENUDRV.tpl").read_text()
sys.stdout.write(TEMPLATE
                 .replace("@PARSE@", PARSE)
                 .replace("@MENU_STRING@", MENU_STRING)
                 .replace("@ADMIN_STRING@", ADMIN_STRING)
                 .replace("@NOT_INSTALLED@", NOT_INSTALLED)
                 .replace("@COMING_SOON@", COMING_SOON)
                 .replace("@ADMIN_NOT_INSTALLED@", ADMIN_NOT_INSTALLED))
