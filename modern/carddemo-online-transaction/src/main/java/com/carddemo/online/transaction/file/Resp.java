package com.carddemo.online.transaction.file;

/** Subset of the CICS file-control RESP codes (DFHRESP) tested by the O4 programs. */
public enum Resp {
  NORMAL,
  NOTFND,
  DUPKEY,
  DUPREC,
  ENDFILE,
  ERROR
}
