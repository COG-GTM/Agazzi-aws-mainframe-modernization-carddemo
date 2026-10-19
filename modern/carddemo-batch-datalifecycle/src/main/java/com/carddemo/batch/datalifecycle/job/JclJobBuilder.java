package com.carddemo.batch.datalifecycle.job;

import com.carddemo.batch.datalifecycle.program.AbendException;
import com.carddemo.batch.datalifecycle.program.Sysout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Builds a Spring Batch job named after a JCL member, with one step per JCL EXEC.
 *
 * <p>SYSOUT of every step is appended to the file in JobParameter {@code sysout} framed as in the
 * golden masters ({@code ---- STEP <s> PGM=<p>} / {@code ---- RC=<n>}); return codes are appended
 * to {@code returnCodes}. A non-zero RC completes the step with exit code {@code RC=<n>}; an abend
 * fails it with {@code ABEND} so the job can be restarted from that step.
 */
public final class JclJobBuilder {

  public static final String MAXCC = "maxcc";
  public static final String ABEND = "ABEND";

  private final JobRepository repository;
  private final PlatformTransactionManager tx;
  private final String name;
  private SimpleJobBuilder job;
  private final java.util.List<JobExecutionListener> listeners = new java.util.ArrayList<>();

  public JclJobBuilder(JobRepository repository, PlatformTransactionManager tx, String name) {
    this.repository = repository;
    this.tx = tx;
    this.name = name;
  }

  public JclJobBuilder step(String stepName, String program, LegacyStep body) {
    return add(stepName, program, body, false);
  }

  /** A step with {@code COND=(0,NE)}: bypassed when any previous step ended with RC > 0. */
  public JclJobBuilder stepCondZeroNe(String stepName, String program, LegacyStep body) {
    return add(stepName, program, body, true);
  }

  private JclJobBuilder add(String stepName, String program, LegacyStep body, boolean cond) {
    Step step =
        new StepBuilder(name + "." + stepName, repository)
            .tasklet(tasklet(stepName, program, body, cond), tx)
            .build();
    job = job == null ? new JobBuilder(name, repository).start(step) : job.next(step);
    return this;
  }

  public JclJobBuilder listener(JobExecutionListener listener) {
    listeners.add(listener);
    return this;
  }

  public Job build() {
    listeners.forEach(job::listener);
    return job.build();
  }

  private static Tasklet tasklet(String stepName, String program, LegacyStep body, boolean cond) {
    return (contribution, chunk) -> {
      ExecutionContext jobCtx =
          contribution.getStepExecution().getJobExecution().getExecutionContext();
      int maxcc = jobCtx.getInt(MAXCC, 0);
      var params = contribution.getStepExecution().getJobParameters();
      if (cond && maxcc != 0) {
        contribution.setExitStatus(new ExitStatus("FLUSHED", "COND=(0,NE) not satisfied"));
        appendRc(params.getString("returnCodes"), stepName + " FLUSH");
        return RepeatStatus.FINISHED;
      }
      Sysout sysout = new Sysout();
      StepEnv env = new StepEnv(stepName, params, sysout, maxcc);
      int rc;
      try {
        rc = body.execute(env);
      } catch (AbendException e) {
        frame(params.getString("sysout"), stepName, program, sysout, AbendException.RETURN_CODE);
        appendRc(params.getString("returnCodes"), stepName + " " + AbendException.RETURN_CODE);
        jobCtx.putInt(MAXCC, Math.max(maxcc, AbendException.RETURN_CODE));
        contribution.setExitStatus(
            new ExitStatus(
                ABEND, "U%04d RC=%d".formatted(e.abendCode(), AbendException.RETURN_CODE)));
        throw e;
      } catch (ConditionCodeException e) {
        sysout.display(e.getMessage());
        frame(params.getString("sysout"), stepName, program, sysout, e.rc());
        appendRc(params.getString("returnCodes"), stepName + " " + e.rc());
        jobCtx.putInt(MAXCC, Math.max(maxcc, e.rc()));
        contribution.setExitStatus(new ExitStatus("RC=" + e.rc(), e.getMessage()));
        throw e;
      }
      frame(params.getString("sysout"), stepName, program, sysout, rc);
      appendRc(params.getString("returnCodes"), stepName + " " + rc);
      jobCtx.putInt(MAXCC, Math.max(maxcc, rc));
      contribution.getStepExecution().getExecutionContext().putInt("rc", rc);
      contribution.setExitStatus(
          rc == 0 ? ExitStatus.COMPLETED : new ExitStatus("RC=" + rc, program + " RC " + rc));
      return RepeatStatus.FINISHED;
    };
  }

  private static void frame(String file, String step, String program, Sysout sysout, int rc) {
    if (file == null || file.isBlank()) {
      return;
    }
    String text =
        "---- STEP " + step + " PGM=" + program + "\n" + sysout.text() + "---- RC=" + rc + "\n";
    append(Path.of(file), text);
  }

  private static void appendRc(String file, String line) {
    if (file != null && !file.isBlank()) {
      append(Path.of(file), line + "\n");
    }
  }

  private static void append(Path path, String text) {
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      Files.writeString(
          path,
          text,
          StandardCharsets.ISO_8859_1,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
