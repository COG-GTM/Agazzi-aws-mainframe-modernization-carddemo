package com.carddemo.batch.orchestration;

import com.carddemo.batch.orchestration.core.OrchestrationProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = TestApp.class)
public abstract class BatchTestBase {

  @Autowired protected JobLauncher launcher;
  @Autowired protected JobRegistry registry;
  @Autowired protected JobExplorer explorer;
  @Autowired protected OrchestrationProperties props;
  @Autowired protected TestSupport.RecordingWait waits;

  @BeforeEach
  void resetState() throws IOException {
    RunLog.reset();
    waits.waits.clear();
    for (Path dir : List.of(props.getDatasetsDir(), props.getTransfer().getLocalDir())) {
      if (Files.exists(dir)) {
        try (Stream<Path> s = Files.walk(dir)) {
          s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
      }
      Files.createDirectories(dir);
    }
  }

  protected Job job(String name) throws Exception {
    return registry.getJob(name);
  }

  protected static JobParametersBuilder params() {
    return new JobParametersBuilder().addString("test.run", UUID.randomUUID().toString());
  }

  protected JobExecution run(String name, JobParameters params) throws Exception {
    return launcher.run(job(name), params);
  }

  protected JobExecution run(String name) throws Exception {
    return run(name, params().toJobParameters());
  }

  protected static StepExecution step(JobExecution e, String name) {
    return e.getStepExecutions().stream()
        .filter(s -> s.getStepName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no step " + name + " in " + e));
  }

  protected static List<String> stepNames(JobExecution e) {
    return e.getStepExecutions().stream()
        .sorted(Comparator.comparing(StepExecution::getId))
        .map(StepExecution::getStepName)
        .toList();
  }

  protected JobExecution awaitEnd(long executionId) throws InterruptedException {
    for (int i = 0; i < 200; i++) {
      JobExecution e = explorer.getJobExecution(executionId);
      if (e != null && !e.isRunning() && e.getStatus() != BatchStatus.STARTING) {
        return e;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("execution " + executionId + " did not end");
  }

  protected Path dataset(String dsn) {
    return props.getDatasetsDir().resolve(dsn);
  }

  /** {@code <modern>/..}: the repository root (app/, modern/). */
  public static Path repoRoot() {
    String root = System.getProperty("modern.root");
    Path modern = root != null ? Path.of(root) : Path.of("").toAbsolutePath();
    while (!Files.isDirectory(modern.resolve("carddemo-parity"))) {
      modern = modern.getParent();
    }
    return modern.toAbsolutePath().getParent();
  }
}
