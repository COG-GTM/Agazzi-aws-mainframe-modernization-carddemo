package com.carddemo.batch.datalifecycle.job;

import com.carddemo.batch.datalifecycle.program.Sysout;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.batch.core.JobParameters;

/**
 * What a step sees: the JobParameters standing in for DD statements ({@code dd.<DDNAME>=<path>})
 * and PARM values, and the SYSOUT of the step.
 */
public final class StepEnv {

  private final String step;
  private final JobParameters params;
  private final Sysout sysout;
  private final int maxcc;

  public StepEnv(String step, JobParameters params, Sysout sysout, int maxcc) {
    this.step = step;
    this.params = params;
    this.sysout = sysout;
    this.maxcc = maxcc;
  }

  /** {@code dd.<STEP>.<DDNAME>} (step override) or {@code dd.<DDNAME>}. */
  public Optional<Path> dd(String name) {
    return param("dd." + step + "." + name).or(() -> param("dd." + name)).map(Path::of);
  }

  /**
   * The DD path, defaulting to the catalogued dataset {@code <datasets>/<dsn>} (JobParameter {@code
   * datasets}, default {@code datasets}).
   */
  public Path dataset(String ddName, String dsn) {
    return dd(ddName).orElseGet(() -> Path.of(param("datasets", "datasets")).resolve(dsn));
  }

  public String step() {
    return step;
  }

  /** A required DD; JCL error (RC 12) when it is not allocated. */
  public Path requireDd(String name) {
    return dd(name)
        .orElseThrow(
            () -> new JclException("DD STATEMENT MISSING: " + name + " (dd." + name + ")"));
  }

  public Optional<String> param(String name) {
    return Optional.ofNullable(params.getString(name)).filter(s -> !s.isBlank());
  }

  public String param(String name, String defaultValue) {
    return param(name).orElse(defaultValue);
  }

  public Sysout sysout() {
    return sysout;
  }

  /** Highest return code of the previous steps of this execution. */
  public int maxcc() {
    return maxcc;
  }

  /** JCL error: the job cannot run the step at all (missing DD, invalid PARM). */
  public static final class JclException extends ConditionCodeException {

    private static final long serialVersionUID = 1L;

    public JclException(String message) {
      super(12, message);
    }
  }
}
