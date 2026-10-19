package com.carddemo.datamigration.io;

import com.carddemo.copybook.ZonedDecimal;
import com.carddemo.datamigration.model.Dataset;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decoder of the CVEXPORT multi-record file written by CBEXPORT (500-byte EBCDIC records, record
 * type in byte 0, REDEFINES per type, mixed DISPLAY / COMP / COMP-3 numerics). Each record is
 * turned into the contracts-v1 record of its dataset (field names without the {@code EXP-} prefix,
 * OCCURS items numbered like the KSDS copybooks).
 */
public final class ExportFileDecoder {

  public static final int LRECL = 500;
  private static final int DATA_OFFSET = 40;
  private static final Charset CP037 = Charset.forName("Cp037");

  private enum Kind {
    TEXT,
    ZONED,
    BINARY,
    PACKED
  }

  private record Field(String name, Kind kind, int length, int scale, boolean signed) {}

  private static final Map<Character, Dataset> TYPES = new LinkedHashMap<>();
  private static final Map<Character, List<Field>> LAYOUTS = new LinkedHashMap<>();

  static {
    TYPES.put('C', Dataset.CUSTDATA);
    TYPES.put('A', Dataset.ACCTDATA);
    TYPES.put('X', Dataset.CARDXREF);
    TYPES.put('T', Dataset.TRANSACT);
    TYPES.put('D', Dataset.CARDDATA);
    LAYOUTS.put(
        'C',
        List.of(
            bin("CUST-ID", 4),
            x("CUST-FIRST-NAME", 25),
            x("CUST-MIDDLE-NAME", 25),
            x("CUST-LAST-NAME", 25),
            x("CUST-ADDR-LINE-1", 50),
            x("CUST-ADDR-LINE-2", 50),
            x("CUST-ADDR-LINE-3", 50),
            x("CUST-ADDR-STATE-CD", 2),
            x("CUST-ADDR-COUNTRY-CD", 3),
            x("CUST-ADDR-ZIP", 10),
            x("CUST-PHONE-NUM-1", 15),
            x("CUST-PHONE-NUM-2", 15),
            zoned("CUST-SSN", 9, 0, false),
            x("CUST-GOVT-ISSUED-ID", 20),
            x("CUST-DOB-YYYY-MM-DD", 10),
            x("CUST-EFT-ACCOUNT-ID", 10),
            x("CUST-PRI-CARD-HOLDER-IND", 1),
            packed("CUST-FICO-CREDIT-SCORE", 2, 0, false)));
    LAYOUTS.put(
        'A',
        List.of(
            zoned("ACCT-ID", 11, 0, false),
            x("ACCT-ACTIVE-STATUS", 1),
            packed("ACCT-CURR-BAL", 7, 2, true),
            zoned("ACCT-CREDIT-LIMIT", 12, 2, true),
            packed("ACCT-CASH-CREDIT-LIMIT", 7, 2, true),
            x("ACCT-OPEN-DATE", 10),
            x("ACCT-EXPIRAION-DATE", 10),
            x("ACCT-REISSUE-DATE", 10),
            zoned("ACCT-CURR-CYC-CREDIT", 12, 2, true),
            new Field("ACCT-CURR-CYC-DEBIT", Kind.BINARY, 8, 2, true),
            x("ACCT-ADDR-ZIP", 10),
            x("ACCT-GROUP-ID", 10)));
    LAYOUTS.put(
        'T',
        List.of(
            x("TRAN-ID", 16),
            x("TRAN-TYPE-CD", 2),
            zoned("TRAN-CAT-CD", 4, 0, false),
            x("TRAN-SOURCE", 10),
            x("TRAN-DESC", 100),
            packed("TRAN-AMT", 6, 2, true),
            bin("TRAN-MERCHANT-ID", 4),
            x("TRAN-MERCHANT-NAME", 50),
            x("TRAN-MERCHANT-CITY", 50),
            x("TRAN-MERCHANT-ZIP", 10),
            x("TRAN-CARD-NUM", 16),
            x("TRAN-ORIG-TS", 26),
            x("TRAN-PROC-TS", 26)));
    LAYOUTS.put(
        'X',
        List.of(
            x("XREF-CARD-NUM", 16), zoned("XREF-CUST-ID", 9, 0, false), bin("XREF-ACCT-ID", 8)));
    LAYOUTS.put(
        'D',
        List.of(
            x("CARD-NUM", 16),
            bin("CARD-ACCT-ID", 8),
            bin("CARD-CVV-CD", 2),
            x("CARD-EMBOSSED-NAME", 50),
            x("CARD-EXPIRAION-DATE", 10),
            x("CARD-ACTIVE-STATUS", 1)));
  }

  /** Header of one export record (EXPORT-REC-TYPE .. EXPORT-REGION-CODE). */
  public record Header(char type, String timestamp, long sequence, String branch, String region) {}

  /** Decoded export: records per dataset, in file order, plus undecodable records. */
  public record Result(
      Map<Dataset, List<SourceRecord>> records, List<SourceDataset.DecodeError> errors) {}

  private ExportFileDecoder() {}

  public static Header header(byte[] r) {
    return new Header(
        new String(r, 0, 1, CP037).charAt(0),
        new String(r, 1, 26, CP037),
        new BigInteger(1, slice(r, 27, 4)).longValue(),
        new String(r, 31, 4, CP037),
        new String(r, 35, 5, CP037));
  }

  public static Result decode(byte[] file) {
    Map<Dataset, List<SourceRecord>> out = new EnumMap<>(Dataset.class);
    List<SourceDataset.DecodeError> errors = new ArrayList<>();
    List<byte[]> recs = PhysicalLayout.splitFixed(file, LRECL);
    for (int i = 0; i < recs.size(); i++) {
      byte[] r = recs.get(i);
      long ordinal = i + 1L;
      try {
        Header h = header(r);
        Dataset ds = TYPES.get(h.type());
        if (ds == null) {
          throw new IllegalArgumentException("unknown EXPORT-REC-TYPE '" + h.type() + "'");
        }
        Map<String, Object> values = new LinkedHashMap<>();
        int off = DATA_OFFSET;
        for (Field f : LAYOUTS.get(h.type())) {
          values.put(f.name(), value(r, off, f));
          off += f.length();
        }
        List<SourceRecord> list = out.computeIfAbsent(ds, k -> new ArrayList<>());
        list.add(new SourceRecord(list.size() + 1L, ds.mapper().fromCobolNames(values)));
      } catch (RuntimeException e) {
        errors.add(new SourceDataset.DecodeError(ordinal, e.getMessage()));
      }
    }
    return new Result(out, errors);
  }

  private static Object value(byte[] r, int off, Field f) {
    return switch (f.kind()) {
      case TEXT -> new String(r, off, f.length(), CP037);
      case ZONED ->
          ZonedDecimal.decode(new String(r, off, f.length(), CP037), f.scale(), f.signed());
      case BINARY -> {
        byte[] b = slice(r, off, f.length());
        BigInteger v = f.signed() ? new BigInteger(b) : new BigInteger(1, b);
        yield new BigDecimal(v, f.scale());
      }
      case PACKED -> unpack(slice(r, off, f.length()), f.scale());
    };
  }

  /** COMP-3: two BCD digits per byte, sign in the last nibble (D = negative). */
  static BigDecimal unpack(byte[] b, int scale) {
    StringBuilder digits = new StringBuilder();
    for (int i = 0; i < b.length; i++) {
      int hi = (b[i] >> 4) & 0x0F;
      int lo = b[i] & 0x0F;
      digits.append(digit(hi));
      if (i < b.length - 1) {
        digits.append(digit(lo));
      } else if (lo == 0x0D || lo == 0x0B) {
        digits.insert(0, '-');
      } else if (lo < 0x0A) {
        throw new IllegalArgumentException("invalid COMP-3 sign nibble " + lo);
      }
    }
    return new BigDecimal(new BigInteger(digits.toString()), scale);
  }

  private static char digit(int n) {
    if (n > 9) {
      throw new IllegalArgumentException("invalid COMP-3 digit nibble " + n);
    }
    return (char) ('0' + n);
  }

  private static Field x(String name, int len) {
    return new Field(name, Kind.TEXT, len, 0, false);
  }

  private static Field zoned(String name, int len, int scale, boolean signed) {
    return new Field(name, Kind.ZONED, len, scale, signed);
  }

  private static Field packed(String name, int bytes, int scale, boolean signed) {
    return new Field(name, Kind.PACKED, bytes, scale, signed);
  }

  private static Field bin(String name, int bytes) {
    return new Field(name, Kind.BINARY, bytes, 0, false);
  }

  private static byte[] slice(byte[] d, int from, int len) {
    byte[] r = new byte[len];
    System.arraycopy(d, from, r, 0, len);
    return r;
  }
}
