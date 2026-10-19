package com.carddemo.copybook.codec;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Variable-block ({@code RECFM=VB}) files: each record is preceded by a 4-byte Record Descriptor
 * Word {@code LL 00 00} with a big-endian length.
 */
public final class VariableBlockFile {

  /** RDW flavour. */
  public enum Rdw {
    /** z/OS: {@code LL} includes the 4 RDW bytes (IMS unload, {@code DBPAUTP0}). */
    IBM(4),
    /** GnuCOBOL {@code RECORDING MODE V}: {@code LL} is the data length only (READACCT VBPS). */
    GNUCOBOL(0);

    private final int included;

    Rdw(int included) {
      this.included = included;
    }
  }

  private VariableBlockFile() {}

  /** Splits a VB file into record payloads (RDW stripped). */
  public static List<byte[]> read(byte[] file, Rdw rdw) {
    List<byte[]> out = new ArrayList<>();
    int i = 0;
    while (i < file.length) {
      if (i + 4 > file.length) {
        throw new IllegalArgumentException("truncated RDW at " + i);
      }
      int ll = ((file[i] & 0xFF) << 8) | (file[i + 1] & 0xFF);
      if (file[i + 2] != 0 || file[i + 3] != 0) {
        throw new IllegalArgumentException("spanned/segmented RDW not supported at " + i);
      }
      int len = ll - rdw.included;
      if (len < 0 || i + 4 + len > file.length) {
        throw new IllegalArgumentException("bad RDW length " + ll + " at " + i);
      }
      byte[] rec = new byte[len];
      System.arraycopy(file, i + 4, rec, 0, len);
      out.add(rec);
      i += 4 + len;
    }
    return out;
  }

  /** Joins record payloads into a VB file. */
  public static byte[] write(List<byte[]> records, Rdw rdw) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] r : records) {
      int ll = r.length + rdw.included;
      if (ll > 0xFFFF) {
        throw new IllegalArgumentException("record too long for RDW: " + r.length);
      }
      out.write(ll >> 8);
      out.write(ll & 0xFF);
      out.write(0);
      out.write(0);
      out.writeBytes(r);
    }
    return out.toByteArray();
  }
}
