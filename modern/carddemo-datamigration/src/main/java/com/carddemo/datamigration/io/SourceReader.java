package com.carddemo.datamigration.io;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.RecordMapper;
import com.carddemo.datamigration.model.SourceFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads and decodes legacy extracts laid out like {@code app/data/ASCII|EBCDIC}. */
public final class SourceReader {

  public static final String EXPORT_FILE = "AWS.M2.CARDDEMO.EXPORT.DATA.PS";

  /**
   * Decodes the requested datasets found in {@code dir}; datasets without a source file are left
   * out of the result.
   */
  public Map<Dataset, SourceDataset> readAll(Path dir, SourceFormat format, List<Dataset> wanted) {
    Map<Dataset, SourceDataset> out = new LinkedHashMap<>();
    if (format == SourceFormat.EXPORT) {
      Path file = dir.resolve(EXPORT_FILE);
      byte[] data = bytes(file);
      ExportFileDecoder.Result res = ExportFileDecoder.decode(data);
      boolean first = true;
      for (Dataset ds : wanted) {
        List<SourceRecord> recs = res.records().get(ds);
        if (recs != null) {
          List<SourceDataset.DecodeError> low = new ArrayList<>();
          out.put(
              ds,
              new SourceDataset(
                  ds,
                  format,
                  EXPORT_FILE,
                  sha256(data),
                  PhysicalLayout.FIXED,
                  normalize(ds, recs, low),
                  first ? res.errors() : List.of(),
                  low));
          first = false;
        }
      }
      return out;
    }
    for (Dataset ds : wanted) {
      Path file = dir.resolve(ds.fileName(format));
      if (Files.isRegularFile(file)) {
        out.put(ds, read(ds, format, file));
      }
    }
    return out;
  }

  public SourceDataset read(Dataset ds, SourceFormat format, Path file) {
    byte[] data = bytes(file);
    boolean ebcdic = format == SourceFormat.EBCDIC;
    PhysicalLayout layout;
    List<byte[]> raw;
    if (ebcdic) {
      layout = PhysicalLayout.FIXED;
      raw = PhysicalLayout.splitFixed(data, ds.recordLength());
    } else {
      PhysicalLayout.Lines lines = PhysicalLayout.splitLines(data, ds.recordLength());
      layout = lines.layout();
      raw = lines.records();
    }
    RecordCodec<Record> codec = ds.codec(ebcdic);
    List<SourceRecord> records = new ArrayList<>(raw.size());
    List<SourceDataset.DecodeError> errors = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      try {
        records.add(new SourceRecord(i + 1L, codec.decode(raw.get(i))));
      } catch (RuntimeException e) {
        errors.add(new SourceDataset.DecodeError(i + 1L, String.valueOf(e.getMessage())));
      }
    }
    List<SourceDataset.DecodeError> low = new ArrayList<>();
    return new SourceDataset(
        ds,
        format,
        file.getFileName().toString(),
        sha256(data),
        layout,
        normalize(ds, records, low),
        errors,
        low);
  }

  static byte[] bytes(Path file) {
    try {
      return Files.readAllBytes(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * PIC X LOW-VALUES (X'00') cannot be stored in PostgreSQL text: they become spaces, as the online
   * programs display them, and are reported as WARN findings.
   */
  static List<SourceRecord> normalize(
      Dataset ds, List<SourceRecord> recs, List<SourceDataset.DecodeError> notes) {
    RecordMapper<Record> m = ds.mapper();
    List<SourceRecord> out = new ArrayList<>(recs.size());
    for (SourceRecord r : recs) {
      Object[] v = m.values(r.value());
      List<String> hit = new ArrayList<>();
      for (int i = 0; i < v.length; i++) {
        if (v[i] instanceof String s && s.indexOf('\u0000') >= 0) {
          v[i] = s.replace('\u0000', ' ');
          hit.add(m.fields().get(i).cobolName());
        }
      }
      if (hit.isEmpty()) {
        out.add(r);
      } else {
        notes.add(
            new SourceDataset.DecodeError(
                r.ordinal(), "LOW-VALUES replaced by spaces in " + String.join(", ", hit)));
        out.add(new SourceRecord(r.ordinal(), m.fromValues(v)));
      }
    }
    return out;
  }

  public static String sha256(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
