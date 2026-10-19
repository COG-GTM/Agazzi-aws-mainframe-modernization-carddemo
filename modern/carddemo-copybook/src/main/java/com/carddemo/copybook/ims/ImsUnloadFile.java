package com.carddemo.copybook.ims;

import com.carddemo.copybook.codec.CodecOptions;
import com.carddemo.copybook.codec.LayoutCodec;
import com.carddemo.copybook.codec.VariableBlockFile;
import com.carddemo.copybook.generated.ims.PendingAuthDetails;
import com.carddemo.copybook.generated.ims.PendingAuthSummary;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * IMS HD unload of the pending-authorization database ({@code DBPAUTP0}): a VB file (RDW inclusive)
 * whose records are either unload control records or segments: an unload prefix ({@code type(2),
 * prefix length(2), segment length(2), segment name(8), …}), the segment data ({@code PAUTSUM0} =
 * {@code CIPAUSMY}, {@code PAUTDTL1} = {@code CIPAUDTY}) and trailing bytes, kept verbatim.
 */
public final class ImsUnloadFile {

  private static final int NAME_OFFSET = 6;

  /** One unload record. */
  public sealed interface Entry permits Control, Summary, Detail {}

  /** Header/trailer record of the unload (or a segment whose data is not valid), verbatim. */
  public record Control(byte[] data) implements Entry {
    @Override
    public boolean equals(Object o) {
      return o instanceof Control c && Arrays.equals(data, c.data);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
      return "Control[" + data.length + " bytes]";
    }
  }

  /** {@code PAUTSUM0} root segment. */
  public record Summary(byte[] prefix, PendingAuthSummary segment, byte[] suffix) implements Entry {
    @Override
    public boolean equals(Object o) {
      return o instanceof Summary s
          && Arrays.equals(prefix, s.prefix)
          && segment.equals(s.segment)
          && Arrays.equals(suffix, s.suffix);
    }

    @Override
    public int hashCode() {
      return 31 * (31 * Arrays.hashCode(prefix) + segment.hashCode()) + Arrays.hashCode(suffix);
    }

    @Override
    public String toString() {
      return "Summary[" + segment + "]";
    }
  }

  /** {@code PAUTDTL1} child segment. */
  public record Detail(byte[] prefix, PendingAuthDetails segment, byte[] suffix) implements Entry {
    @Override
    public boolean equals(Object o) {
      return o instanceof Detail d
          && Arrays.equals(prefix, d.prefix)
          && segment.equals(d.segment)
          && Arrays.equals(suffix, d.suffix);
    }

    @Override
    public int hashCode() {
      return 31 * (31 * Arrays.hashCode(prefix) + segment.hashCode()) + Arrays.hashCode(suffix);
    }

    @Override
    public String toString() {
      return "Detail[" + segment + "]";
    }
  }

  private static final LayoutCodec<PendingAuthSummary> SUMMARY =
      PendingAuthSummary.codec(CodecOptions.EBCDIC);
  private static final LayoutCodec<PendingAuthDetails> DETAIL =
      PendingAuthDetails.codec(CodecOptions.EBCDIC);

  private ImsUnloadFile() {}

  /** Decodes a whole unload file. */
  public static List<Entry> read(byte[] file) {
    List<Entry> out = new ArrayList<>();
    for (byte[] rec : VariableBlockFile.read(file, VariableBlockFile.Rdw.IBM)) {
      out.add(decode(rec));
    }
    return out;
  }

  /** Encodes entries back into an unload file. */
  public static byte[] write(List<Entry> entries) {
    List<byte[]> recs = new ArrayList<>();
    for (Entry e : entries) {
      recs.add(encode(e));
    }
    return VariableBlockFile.write(recs, VariableBlockFile.Rdw.IBM);
  }

  static Entry decode(byte[] rec) {
    if (rec.length >= NAME_OFFSET + 8) {
      int prefixLen = u16(rec, 2);
      int segLen = u16(rec, 4);
      String segment = segmentName(rec);
      if (prefixLen >= NAME_OFFSET + 8 && prefixLen + segLen <= rec.length) {
        byte[] prefix = Arrays.copyOf(rec, prefixLen);
        byte[] data = Arrays.copyOfRange(rec, prefixLen, prefixLen + segLen);
        byte[] suffix = Arrays.copyOfRange(rec, prefixLen + segLen, rec.length);
        try {
          if ("PAUTSUM0".equals(segment) && segLen == SUMMARY.recordLength()) {
            return new Summary(prefix, SUMMARY.decode(data), suffix);
          }
          if ("PAUTDTL1".equals(segment) && segLen == DETAIL.recordLength()) {
            return new Detail(prefix, DETAIL.decode(data), suffix);
          }
        } catch (IllegalArgumentException notDecodable) {
          // e.g. a blank PAUTSUM0 (PA-ACCT-ID = spaces): kept verbatim as a control record
        }
      }
    }
    return new Control(rec.clone());
  }

  static byte[] encode(Entry e) {
    return switch (e) {
      case Control c -> c.data().clone();
      case Summary s -> concat(s.prefix(), SUMMARY.encode(s.segment()), s.suffix());
      case Detail d -> concat(d.prefix(), DETAIL.encode(d.segment()), d.suffix());
    };
  }

  private static int u16(byte[] b, int off) {
    return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
  }

  private static String segmentName(byte[] rec) {
    if (rec.length < NAME_OFFSET + 8) {
      return "";
    }
    Charset cs = CodecOptions.CP037;
    return new String(rec, NAME_OFFSET, 8, cs);
  }

  private static byte[] concat(byte[]... parts) {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    for (byte[] p : parts) {
      out.writeBytes(p);
    }
    return out.toByteArray();
  }
}
