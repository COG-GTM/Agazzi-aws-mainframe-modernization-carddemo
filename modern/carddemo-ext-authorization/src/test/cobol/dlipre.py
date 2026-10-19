#!/usr/bin/env python3
"""Harness-only rewrite of an EXEC DLI batch program for GnuCOBOL: each EXEC DLI ... END-EXEC
becomes CALL 'DLISTUB' USING func segment area DIBSTAT, DIBSTAT is declared in WORKING-STORAGE
and the PCB parameters of the PROCEDURE DIVISION are dropped. Never applied to app/."""
import re, sys

src = open(sys.argv[1], encoding='latin-1').read().splitlines()
out, i = [], 0
while i < len(src):
    line = src[i]
    code = line[6:72] if len(line) > 6 else ''
    if re.search(r'\bWORKING-STORAGE\s+SECTION', code):
        out += [line, '       01 DLI-DIB.', '          05 DIBSTAT PIC X(02).']
    elif re.search(r'\bPROCEDURE\s+DIVISION\s+USING', code):
        while not src[i][6:72].rstrip().endswith('.'):
            i += 1
        out.append('       PROCEDURE DIVISION.')
    elif re.search(r'\bEXEC\s+DLI\b', code) and code[0:1] != '*':
        block = []
        while True:
            block.append(src[i][6:72])
            if 'END-EXEC' in src[i][6:72]:
                break
            i += 1
        text = ' '.join(block)
        func = re.search(r'EXEC\s+DLI\s+(\w+)', text).group(1)
        seg = re.search(r'SEGMENT\s*\(\s*([\w-]+)\s*\)', text)
        area = re.search(r'(?:INTO|FROM|ID)\s*\(\s*([\w-]+)\s*\)', text)
        out += [f"           CALL 'DLISTUB' USING '{func:<4}'",
                f"                '{(seg.group(1) if seg else ''):<8}'",
                f"                {area.group(1)} DIBSTAT",
                '           END-CALL']
    else:
        out.append(line)
    i += 1
open(sys.argv[2], 'w', encoding='latin-1').write('\n'.join(out) + '\n')
