#!/usr/bin/env python3
"""Decode the IMS HD unload AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat (EBCDIC, variable records with a
2-byte big-endian length prefix) into PAUDBUNL-format ASCII files:
  PAUTSUM0.bin : 100-byte root segments (CIPAUSMY)
  PAUTDTL1.bin : 6-byte packed ACCNTID + 200-byte child segment (CIPAUDTY)
Text/zoned fields are converted cp037 -> latin-1, COMP/COMP-3 bytes are kept as is."""
import sys, pathlib

SUM_TEXT = [(6, 9), (15, 1), (16, 10), (66, 34)]
DTL_TEXT = [(8, 66), (88, 9), (97, 103)]


def conv(seg, ranges):
    b = bytearray(seg)
    for off, ln in ranges:
        b[off:off + ln] = seg[off:off + ln].decode('cp037').encode('latin-1')
    return bytes(b)


def records(data):
    i = 0
    while i < len(data):
        ln = int.from_bytes(data[i:i + 2], 'big')
        yield data[i:i + ln]
        i += ln


def decode(path):
    roots, children, acct = [], [], None
    for rec in records(pathlib.Path(path).read_bytes()):
        name = rec[10:18].decode('cp037', 'replace')
        if name == 'PAUTSUM0':
            seg = rec[39:139]
            acct = seg[0:6]
            roots.append(conv(seg, SUM_TEXT))
        elif name == 'PAUTDTL1':
            children.append(acct + conv(rec[39:239], DTL_TEXT))
    return roots, children


if __name__ == '__main__':
    src, out = sys.argv[1], pathlib.Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    r, c = decode(src)
    (out / 'PAUTSUM0.bin').write_bytes(b''.join(r))
    (out / 'PAUTDTL1.bin').write_bytes(b''.join(c))
    print(f'roots={len(r)} children={len(c)}')
