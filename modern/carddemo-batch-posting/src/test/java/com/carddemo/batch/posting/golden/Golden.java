package com.carddemo.batch.posting.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.posting.cbtrn02c.RejectRecord;
import com.carddemo.batch.posting.dataset.LineSequentialFile;
import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.CoreCodecs;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Golden masters of {@code carddemo-parity/src/test/resources/golden}: inputs and field-level
 * comparison of the Java outputs with the GnuCOBOL ones (numeric fields by value, as {@code
 * RecordComparator} does: GnuCOBOL writes positive zoned numbers without overpunch).
 */
public final class Golden {

  private Golden() {}

  public static Path modernRoot() {
    String root = System.getProperty("modern.root");
    return root != null ? Path.of(root) : Path.of("..").toAbsolutePath().normalize();
  }

  public static Path goldenRoot() {
    return modernRoot().resolve("carddemo-parity/src/test/resources/golden");
  }

  /** Phase-0 golden of {@code golden/batch/<job>}. */
  public static Path batch(String job) {
    return goldenRoot().resolve("batch").resolve(job);
  }

  /** B1 golden of {@code golden/b1-posting/<job>}. */
  public static Path b1(String job) {
    return goldenRoot().resolve("b1-posting").resolve(job);
  }

  public static Path seed(String name) {
    return modernRoot().resolve("../app/data/ASCII").resolve(name).normalize();
  }

  public static void copy(Path from, Path dir, String dsname) {
    try {
      Files.createDirectories(dir);
      Files.copy(from, dir.resolve(dsname + ".dat"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<String> expected(Path job, String output, int lrecl) {
    return LineSequentialFile.read(job.resolve("expected").resolve(output + ".dat"), lrecl);
  }

  public static List<String> returnCodes(Path job) {
    return lines(job.resolve("expected/RETURN-CODES.txt"));
  }

  /** Program DISPLAY lines of the golden SYSOUT (harness framing removed, right-trimmed). */
  public static List<String> sysout(Path job) {
    return lines(job.resolve("expected/SYSOUT.txt")).stream()
        .filter(l -> !l.startsWith("---- "))
        .toList();
  }

  public static List<String> rtrim(List<String> lines) {
    return lines.stream().map(String::stripTrailing).toList();
  }

  private static List<String> lines(Path p) {
    try {
      return rtrim(Files.readAllLines(p, java.nio.charset.StandardCharsets.ISO_8859_1));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Field-by-field comparison of two record files through the copybook codec. */
  public static <R> void assertSameRecords(
      String what, List<String> expected, List<String> actual, RecordCodec<R> codec) {
    assertThat(actual).as(what + " record count").hasSize(expected.size());
    List<R> e = new ArrayList<>();
    List<R> a = new ArrayList<>();
    for (int i = 0; i < expected.size(); i++) {
      e.add(codec.decodeLine(expected.get(i)));
      a.add(codec.decodeLine(actual.get(i)));
    }
    for (int i = 0; i < e.size(); i++) {
      assertThat(a.get(i)).as(what + " record " + (i + 1)).isEqualTo(e.get(i));
    }
  }

  /** DALYREJS: REJECT-TRAN-DATA through CVTRA06Y, trailer byte-exact. */
  public static void assertSameRejects(List<String> expected, List<String> actual) {
    assertThat(actual).as("DALYREJS record count").hasSize(expected.size());
    for (int i = 0; i < expected.size(); i++) {
      RejectRecord e = RejectRecord.decode(expected.get(i));
      RejectRecord a = RejectRecord.decode(actual.get(i));
      assertThat(CoreCodecs.DAILY_TRANSACTION.decodeLine(a.transactionData()))
          .as("DALYREJS record " + (i + 1))
          .isEqualTo(CoreCodecs.DAILY_TRANSACTION.decodeLine(e.transactionData()));
      assertThat(a.reason()).as("reject reason " + (i + 1)).isEqualTo(e.reason());
      assertThat(a.description()).as("reject desc " + (i + 1)).isEqualTo(e.description());
    }
  }
}
