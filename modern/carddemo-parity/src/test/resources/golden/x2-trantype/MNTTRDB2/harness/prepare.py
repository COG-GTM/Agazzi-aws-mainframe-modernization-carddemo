#!/usr/bin/env python3
"""Replace the EXEC SQL blocks of COBTUPDT with CALL 'SQLSTUB' (DB2 stand-in, see sqlstub.c)."""
import re
import sys

src, dst = sys.argv[1], sys.argv[2]
lines = open(src, encoding="ascii").read().splitlines()
out, block = [], None
CALL = "           CALL 'SQLSTUB' USING BY CONTENT '{op}' INPUT-REC-NUMBER\n" \
       "                INPUT-REC-DESC BY REFERENCE SQLCODE END-CALL{dot}"
for ln in lines:
    code = ln[6:72] if len(ln) > 6 else ""
    if block is None and "EXEC SQL" in code:
        block = []
    if block is not None:
        block.append(code)
        if "END-EXEC" in code:
            text = " ".join(block)
            dot = "." if re.search(r"END-EXEC\s*\.", text) else ""
            if "INCLUDE SQLCA" in text:
                out.append("       01  SQLCODE                 PIC S9(9) COMP-5 VALUE 0.")
            elif "INCLUDE" in text:
                pass
            else:
                op = "I" if "INSERT" in text else "U" if "UPDATE" in text else "D"
                out.append(CALL.format(op=op, dot=dot))
            block = None
        continue
    out.append(ln[:72])
open(dst, "w", encoding="ascii").write("\n".join(out) + "\n")
