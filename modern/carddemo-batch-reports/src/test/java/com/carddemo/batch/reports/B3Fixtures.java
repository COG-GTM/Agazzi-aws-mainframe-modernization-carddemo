package com.carddemo.batch.reports;

import com.carddemo.batch.reports.support.AbendExitStatus;
import com.carddemo.batch.reports.support.DatasetCatalog;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.yaml.snakeyaml.Yaml;

/** Golden fixtures: paths, scenario materialisation from {@code job.yaml}, return codes. */
public final class B3Fixtures {

  public static final Path MODERN =
      Path.of(System.getProperty("modern.root", "..")).toAbsolutePath().normalize();
  public static final Path GOLDEN = MODERN.resolve("carddemo-parity/src/test/resources/golden");
  public static final Path B3 = GOLDEN.resolve("b3-reports");
  public static final Path SEED = MODERN.resolve("../app/data/ASCII").normalize();

  private B3Fixtures() {}

  public static Path seed(String name) {
    return SEED.resolve(name);
  }

  public static Path batchGolden(String rel) {
    return GOLDEN.resolve("batch").resolve(rel);
  }

  /** Copies every {@code input:} dataset of the scenario into {@code dir} as {@code <DD>.dat}. */
  @SuppressWarnings("unchecked")
  public static void materialise(String scenario, Path dir) {
    Path jobDir = B3.resolve(scenario);
    Map<String, Object> job;
    try {
      job = new Yaml().load(Files.readString(jobDir.resolve("job.yaml")));
      Files.createDirectories(dir);
      Map<String, Map<String, Object>> ds = (Map<String, Map<String, Object>>) job.get("datasets");
      for (Map.Entry<String, Map<String, Object>> e : ds.entrySet()) {
        Object input = e.getValue().get("input");
        if (input != null) {
          Files.copy(
              resolve(jobDir, input.toString()),
              dir.resolve(e.getKey() + ".dat"),
              java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }

  private static Path resolve(Path jobDir, String spec) {
    if (spec.startsWith("seed:")) {
      return SEED.resolve(spec.substring(5));
    }
    if (spec.startsWith("golden:")) {
      return B3.resolve(spec.substring(7)).normalize();
    }
    return jobDir.resolve(spec);
  }

  public static JobParameters params(Path dir, String... kv) {
    JobParametersBuilder b =
        new JobParametersBuilder().addString(DatasetCatalog.DATASET_DIR, dir.toString());
    for (int i = 0; i < kv.length; i += 2) {
      b.addString(kv[i], kv[i + 1]);
    }
    return b.toJobParameters();
  }

  /** {@code <STEP> <RC>} per executed step, as in the golden {@code RETURN-CODES.txt}. */
  public static Map<String, Integer> returnCodes(JobExecution e) {
    Map<String, Integer> rcs = new LinkedHashMap<>();
    for (StepExecution s : e.getStepExecutions()) {
      rcs.put(s.getStepName(), AbendExitStatus.returnCode(s.getExitStatus()));
    }
    return rcs;
  }

  public static Map<String, Integer> goldenReturnCodes(String scenario) {
    Map<String, Integer> rcs = new LinkedHashMap<>();
    for (String l : lines(B3.resolve(scenario).resolve("expected/RETURN-CODES.txt"))) {
      String[] p = l.trim().split(" ");
      rcs.put(p[0], Integer.parseInt(p[1]));
    }
    return rcs;
  }

  public static List<String> lines(Path p) {
    try {
      return Files.exists(p)
          ? Files.readAllLines(p, java.nio.charset.StandardCharsets.ISO_8859_1)
          : List.of();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static byte[] bytes(Path p) {
    try {
      return Files.exists(p) ? Files.readAllBytes(p) : new byte[0];
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
