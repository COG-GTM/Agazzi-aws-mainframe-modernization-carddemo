package com.carddemo.datamigration.export;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.datamigration.io.PhysicalLayout;
import com.carddemo.datamigration.io.SourceRecord;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.Organization;
import com.carddemo.datamigration.model.SourceFormat;
import com.carddemo.datamigration.store.RelationalStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code relationalToVsam}: unloads the V1 tables back to the legacy record layout. KSDS are
 * written in VSAM key order (unsigned bytes in the target code page), PS/ESDS/RRDS in surrogate
 * order. ASCII files reuse the physical layout captured at import, so a round trip is
 * byte-identical; EBCDIC files are fixed-length CP037 like the mainframe PS unloads.
 */
public class VsamExporter {

  private final RelationalStore store;

  public VsamExporter(RelationalStore store) {
    this.store = store;
  }

  /** Result of one unloaded dataset. */
  public record Unloaded(Dataset dataset, Path file, int records) {}

  public Unloaded unload(Dataset ds, SourceFormat target, Path outDir) {
    boolean ebcdic = target == SourceFormat.EBCDIC;
    List<byte[]> encoded = encode(ds, store.readAll(ds), ebcdic);
    PhysicalLayout layout =
        ebcdic ? PhysicalLayout.FIXED : store.asciiLayout(ds).orElse(PhysicalLayout.LINES);
    Path file = outDir.resolve(exportFileName(ds, target));
    try {
      Files.createDirectories(outDir);
      Files.write(file, layout.join(encoded));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return new Unloaded(ds, file, encoded.size());
  }

  /** Encodes and orders records as the VSAM unload would. */
  public static List<byte[]> encode(Dataset ds, List<SourceRecord> rows, boolean ebcdic) {
    RecordCodec<Record> codec = ds.codec(ebcdic);
    List<SourceRecord> sorted = new ArrayList<>(rows);
    sorted.sort((a, b) -> Long.compare(a.ordinal(), b.ordinal()));
    List<byte[]> out = new ArrayList<>(sorted.size());
    for (SourceRecord r : sorted) {
      out.add(codec.encode(r.value()));
    }
    if (ds.organization() == Organization.KSDS) {
      int k = ds.keyLength();
      out.sort((a, b) -> Arrays.compareUnsigned(Arrays.copyOf(a, k), Arrays.copyOf(b, k)));
    }
    return out;
  }

  /** Same file names as {@code app/data}; ESDS/RRDS get their own suffix. */
  public static String exportFileName(Dataset ds, SourceFormat target) {
    String base =
        ds.fileName(target == SourceFormat.EBCDIC ? SourceFormat.EBCDIC : SourceFormat.ASCII);
    return switch (ds.organization()) {
      case ESDS ->
          target == SourceFormat.EBCDIC ? base.replace(".PS", ".ESDS.PS") : "usrsec-esds.txt";
      case RRDS ->
          target == SourceFormat.EBCDIC ? base.replace(".PS", ".RRDS.PS") : "usrsec-rrds.txt";
      default -> base;
    };
  }

  /** Byte comparison of an unloaded file with the original extract. */
  public record Comparison(
      Dataset dataset,
      String file,
      boolean identical,
      long firstDiff,
      long expectedSize,
      long actualSize) {}

  public static Comparison compare(Dataset ds, Path actual, Path expected) {
    try {
      byte[] a = Files.readAllBytes(actual);
      byte[] e = Files.readAllBytes(expected);
      long diff = Arrays.mismatch(a, e);
      return new Comparison(
          ds, expected.getFileName().toString(), diff < 0, diff, e.length, a.length);
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
