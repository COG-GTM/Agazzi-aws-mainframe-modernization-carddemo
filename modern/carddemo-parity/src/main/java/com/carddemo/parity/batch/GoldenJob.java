package com.carddemo.parity.batch;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * A golden master captured by {@code modern/tools/golden/run-cobol.sh <JOB>}: {@code job.yaml}
 * (datasets with LRECL and copybook layout) plus {@code expected/*.dat|*.txt|*.bin}.
 */
public record GoldenJob(
    String name,
    Path dir,
    String businessTimestamp,
    Map<String, Dataset> datasets,
    List<String> outputs) {

  /** A dataset of the job: organisation, record length and copybook layout. */
  public record Dataset(String name, String org, int lrecl, String layout) {}

  /** Root of the batch goldens: {@code carddemo-parity/src/test/resources/golden/batch}. */
  public static Path root(Path modernRoot) {
    return modernRoot.resolve("carddemo-parity/src/test/resources/golden/batch");
  }

  /** Jobs listed in {@code golden/batch/ORDER}. */
  public static List<String> jobNames(Path modernRoot) throws IOException {
    return Files.readAllLines(root(modernRoot).resolve("ORDER")).stream()
        .map(String::trim)
        .filter(s -> !s.isEmpty() && !s.startsWith("#"))
        .toList();
  }

  @SuppressWarnings("unchecked")
  public static GoldenJob load(Path modernRoot, String job) throws IOException {
    Path dir = root(modernRoot).resolve(job);
    Map<String, Object> y;
    try (Reader r = Files.newBufferedReader(dir.resolve("job.yaml"), StandardCharsets.UTF_8)) {
      y = new Yaml().load(r);
    }
    Map<String, Dataset> ds = new LinkedHashMap<>();
    Map<String, Map<String, Object>> raw =
        (Map<String, Map<String, Object>>) y.getOrDefault("datasets", Map.of());
    raw.forEach(
        (n, d) ->
            ds.put(
                n,
                new Dataset(
                    n,
                    String.valueOf(d.getOrDefault("org", "ps")),
                    ((Number) d.getOrDefault("lrecl", 0)).intValue(),
                    (String) d.get("layout"))));
    List<String> outputs = new ArrayList<>((List<String>) y.getOrDefault("outputs", List.of()));
    return new GoldenJob(job, dir, String.valueOf(y.get("business_timestamp")), ds, outputs);
  }

  public Path expected(String file) {
    return dir.resolve("expected").resolve(file);
  }

  /** Expected file of an output dataset ({@code <DD>.dat}, or {@code .bin} for binary layouts). */
  public Path expectedOutput(String dataset) {
    Path dat = expected(dataset + ".dat");
    return Files.exists(dat) ? dat : expected(dataset + ".bin");
  }

  /** {@code RETURN-CODES.txt}: step name to return code. */
  public Map<String, Integer> returnCodes() throws IOException {
    Map<String, Integer> rc = new LinkedHashMap<>();
    for (String line : Files.readAllLines(expected("RETURN-CODES.txt"))) {
      String[] p = line.trim().split("\\s+");
      if (p.length == 2) {
        rc.put(p[0], Integer.parseInt(p[1]));
      }
    }
    return rc;
  }
}
