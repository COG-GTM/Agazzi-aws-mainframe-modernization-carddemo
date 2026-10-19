package com.carddemo.batch.interest.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Golden master produced by {@code modern/tools/golden/run-cobol.sh} (job.yaml + expected/). Local
 * reader: carddemo-parity's GoldenJob cannot be a dependency (parity → app → batch-interest).
 */
public final class GoldenFixture {

  private final Path dir;
  private final Map<String, Object> job;

  private GoldenFixture(Path dir) {
    this.dir = dir;
    try {
      this.job = new Yaml().load(Files.readString(dir.resolve("job.yaml")));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Path modernRoot() {
    String p = System.getProperty("modern.root");
    Path root = p != null ? Path.of(p) : Path.of("..").toAbsolutePath().normalize();
    return Files.isDirectory(root.resolve("carddemo-parity"))
        ? root
        : Path.of("..").toAbsolutePath().normalize();
  }

  static Path goldenRoot() {
    return modernRoot().resolve("carddemo-parity/src/test/resources/golden");
  }

  /** {@code batch/INTCALC} or {@code b2-interest/INTCALC-SEED}. */
  public static GoldenFixture load(String relative) {
    return new GoldenFixture(goldenRoot().resolve(relative));
  }

  public Path dir() {
    return dir;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> dataset(String name) {
    return (Map<String, Object>) ((Map<String, Object>) job.get("datasets")).get(name);
  }

  public int lrecl(String name) {
    return ((Number) dataset(name).get("lrecl")).intValue();
  }

  /** Input records of a dataset (seed:, golden: or fixture-relative), padded to LRECL. */
  public List<String> input(String name) {
    Object spec = dataset(name).get("input");
    if (spec == null) {
      return List.of();
    }
    String s = spec.toString();
    Path p;
    if (s.startsWith("seed:")) {
      p = modernRoot().getParent().resolve("app/data/ASCII").resolve(s.substring(5));
    } else if (s.startsWith("golden:")) {
      p = goldenRoot().resolve("batch").resolve(s.substring(7));
    } else {
      p = dir.resolve(s);
    }
    return records(p, lrecl(name));
  }

  public Path inputPath(String name) {
    String s = dataset(name).get("input").toString();
    return s.startsWith("seed:")
        ? modernRoot().getParent().resolve("app/data/ASCII").resolve(s.substring(5))
        : dir.resolve(s);
  }

  public List<String> expected(String name, int lrecl) {
    return records(dir.resolve("expected").resolve(name + ".dat"), lrecl);
  }

  public List<String> expectedFile(String relative) {
    try {
      return Files.readAllLines(dir.resolve(relative), StandardCharsets.US_ASCII);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** RETURN-CODES.txt as "STEP RC" lines. */
  public List<String> returnCodes() {
    return expectedFile("expected/RETURN-CODES.txt");
  }

  /** SYSOUT.txt without the harness framing ({@code ---- } markers, libcob runtime warnings). */
  public List<String> sysout() {
    return expectedFile("expected/SYSOUT.txt").stream()
        .filter(l -> !l.startsWith("---- ") && !l.startsWith("libcob: "))
        .toList();
  }

  public static List<String> records(Path p, int lrecl) {
    if (!Files.exists(p)) {
      return List.of();
    }
    try {
      List<String> out = new ArrayList<>();
      for (String line : Files.readAllLines(p, StandardCharsets.US_ASCII)) {
        String l = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
        if (!l.isEmpty()) {
          out.add(l.length() >= lrecl ? l.substring(0, lrecl) : l + " ".repeat(lrecl - l.length()));
        }
      }
      return out;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
