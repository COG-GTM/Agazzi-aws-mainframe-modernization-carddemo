package com.carddemo.batch.posting.dataset;

import com.carddemo.batch.posting.support.CobolAbendException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed-length records stored one per line (the format of {@code app/data/ASCII} and of the golden
 * masters): each line is right-padded with spaces to LRECL, longer lines are malformed.
 */
public final class LineSequentialFile {

  private LineSequentialFile() {}

  public static List<String> read(Path path, int lrecl) {
    if (!Files.exists(path)) {
      throw new CobolAbendException("DATASET NOT FOUND " + path.getFileName(), "35");
    }
    try (InputStream in = Files.newInputStream(path)) {
      return read(in, lrecl, path.getFileName().toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static List<String> read(InputStream in, int lrecl, String name) throws IOException {
    String data = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
    List<String> out = new ArrayList<>();
    int lineNo = 0;
    for (String line : data.split("\n", -1)) {
      lineNo++;
      String l = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
      if (l.isEmpty()) {
        continue;
      }
      if (l.length() > lrecl) {
        throw new CobolAbendException(
            "MALFORMED RECORD " + lineNo + " IN " + name + ": LENGTH " + l.length() + " > " + lrecl,
            "04");
      }
      out.add(Keys.pic(l, lrecl));
    }
    return out;
  }

  public static void write(Path path, List<String> records) {
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      try (OutputStream out = Files.newOutputStream(path)) {
        write(out, records);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Replaces several files as one unit: every file is first staged as {@code <name>.tmp}; only when
   * all are staged are they moved into place (atomic rename), so a failed write publishes nothing
   * and leaves the previous versions intact.
   */
  public static void replaceAll(Map<Path, List<String>> files) {
    Map<Path, Path> staged = new LinkedHashMap<>();
    try {
      for (Map.Entry<Path, List<String>> f : files.entrySet()) {
        Path tmp = f.getKey().resolveSibling(f.getKey().getFileName() + ".tmp");
        staged.put(tmp, f.getKey());
        write(tmp, f.getValue());
      }
    } catch (RuntimeException e) {
      staged.keySet().forEach(LineSequentialFile::deleteQuietly);
      throw e;
    }
    try {
      for (Map.Entry<Path, Path> s : staged.entrySet()) {
        Files.move(
            s.getKey(),
            s.getValue(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void deleteQuietly(Path p) {
    try {
      if (Files.isRegularFile(p)) {
        Files.delete(p);
      }
    } catch (IOException e) {
      // best effort cleanup of a staged file
    }
  }

  public static void write(OutputStream out, List<String> records) throws IOException {
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    for (String r : records) {
      buf.write(r.getBytes(StandardCharsets.ISO_8859_1));
      buf.write('\n');
    }
    out.write(buf.toByteArray());
  }
}
