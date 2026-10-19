package com.carddemo.datamigration.cli;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * {@code migrate-data}: {@code java -jar carddemo-datamigration-*-cli.jar --job=vsamToRelational
 * --source=ebcdic --input=app/data/EBCDIC}. Exit code 0 = COMPLETED, 1 = job FAILED (validation
 * errors, reconciliation or parity mismatch), 2 = usage error.
 */
public final class MigrateDataCli {

  static final Set<String> JOB_OPTIONS =
      Set.of(
          "source",
          "input",
          "output",
          "datasets",
          "mode",
          "on-error",
          "target",
          "compare",
          "verify",
          "listcat");

  private MigrateDataCli() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out));
  }

  public static int run(String[] args, PrintStream out) {
    Map<String, String> opts = parse(args);
    String jobName = opts.get("job");
    if (jobName == null || opts.containsKey("help")) {
      out.println(usage());
      return jobName == null && !opts.containsKey("help") ? 2 : 0;
    }
    SpringApplication app = new SpringApplication(MigrateDataApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.setDefaultProperties(
        Map.of(
            "spring.config.name", "migrate-data",
            "carddemo.datamigration.enabled", "true",
            "carddemo.datamigration.cli", "true",
            "spring.batch.job.enabled", "false"));
    try (ConfigurableApplicationContext ctx = app.run(args)) {
      if (!ctx.containsBean(jobName)) {
        out.println("unknown job '" + jobName + "'\n" + usage());
        return 2;
      }
      Job job = ctx.getBean(jobName, Job.class);
      JobParametersBuilder b =
          new JobParametersBuilder()
              .addLong("run.id", nextRunId(ctx.getBean(JobExplorer.class), jobName));
      opts.forEach(
          (k, v) -> {
            if (JOB_OPTIONS.contains(k)) {
              b.addString(k.equals("on-error") ? "onError" : k, v, false);
            }
          });
      JobParameters params = b.toJobParameters();
      JobExecution exec = ctx.getBean(JobLauncher.class).run(job, params);
      out.println(
          jobName
              + " "
              + exec.getStatus()
              + " "
              + exec.getStepExecutions().stream()
                  .map(s -> s.getStepName() + "=" + s.getStatus() + "(w" + s.getWriteCount() + ")")
                  .toList());
      exec.getAllFailureExceptions().forEach(t -> out.println("  " + t.getMessage()));
      return exec.getStatus() == BatchStatus.COMPLETED ? 0 : 1;
    } catch (Exception e) {
      out.println("migrate-data failed: " + e.getMessage());
      return 1;
    }
  }

  private static long nextRunId(JobExplorer explorer, String jobName) {
    try {
      return explorer.getJobInstanceCount(jobName) + 1;
    } catch (org.springframework.batch.core.launch.NoSuchJobException e) {
      return 1;
    }
  }

  static Map<String, String> parse(String[] args) {
    Map<String, String> m = new LinkedHashMap<>();
    for (String a : args) {
      if (a.startsWith("--")) {
        int eq = a.indexOf('=');
        m.put(eq < 0 ? a.substring(2) : a.substring(2, eq), eq < 0 ? "true" : a.substring(eq + 1));
      }
    }
    return m;
  }

  static String usage() {
    return String.join(
        "\n",
        "usage: migrate-data --job=<job> [options] [--spring.datasource.url=...]",
        "  jobs: vsamToRelational | relationalToVsam | usrsecToBcrypt | usrsecRollback |",
        "        usrsecPurgeBackup | applyConstraints",
        "  --source=ascii|ebcdic|export   extract format (vsamToRelational, default ascii)",
        "  --input=<dir>                  extract directory (default app/data/ASCII|EBCDIC)",
        "  --output=<dir>                 reports / unloaded files (default target/migrate-data)",
        "  --datasets=ALL|ACCTDATA,...    datasets to process",
        "  --mode=upsert|replace          replace empties the tables first",
        "  --on-error=abort|skip          abort (default) or skip rejected records",
        "  --listcat=<LISTCAT.txt>        catalog REC-TOTAL shown in reconciliation.md",
        "  --target=ascii|ebcdic          relationalToVsam output format",
        "  --compare=<dir> [--verify]     byte comparison with the original extract");
  }
}
