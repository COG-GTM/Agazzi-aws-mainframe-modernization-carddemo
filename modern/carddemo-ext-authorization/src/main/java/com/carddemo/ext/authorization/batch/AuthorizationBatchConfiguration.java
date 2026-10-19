package com.carddemo.ext.authorization.batch;

import com.carddemo.ext.authorization.codec.PendingAuthSegments;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch jobs of X1: {@code CBPAUP0J} (parameter {@code sysin}, e.g. {@code
 * 00,00001,00001,Y}), {@code UNLDPADB} / {@code LOADPADB} (parameters {@code roots}, {@code
 * children}) and {@code DBPAUTP0} (parameter {@code input}: EBCDIC HD unload file).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnAuthorizationExtension
public class AuthorizationBatchConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationBatchConfiguration.class);

  @Bean
  Job cbpaup0jJob(JobRepository repo, PlatformTransactionManager tx, PendingAuthPurgeService s) {
    Step step =
        new StepBuilder("CBPAUP0C", repo)
            .tasklet(
                (contribution, ctx) -> {
                  String sysin =
                      contribution.getStepExecution().getJobParameters().getString("sysin", "");
                  PurgeReport r = s.purge(PurgeParameters.parse(sysin));
                  ctx.getStepContext().getStepExecution().getExecutionContext().put("report", r);
                  LOG.info("CBPAUP0C {}", r);
                  return RepeatStatus.FINISHED;
                },
                tx)
            .build();
    return new JobBuilder("CBPAUP0J", repo).start(step).build();
  }

  @Bean
  Job unldpadbJob(
      JobRepository repo, PlatformTransactionManager tx, PendingAuthUnloadLoadService s) {
    Step step =
        new StepBuilder("PAUDBUNL", repo)
            .tasklet(
                (contribution, ctx) -> {
                  var p = contribution.getStepExecution().getJobParameters();
                  PendingAuthUnloadLoadService.Unload u = s.unload(PendingAuthSegments.ASCII);
                  publishPair(
                      Path.of(p.getString("roots")),
                      u.roots(),
                      Path.of(p.getString("children")),
                      u.children());
                  return RepeatStatus.FINISHED;
                },
                tx)
            .build();
    return new JobBuilder("UNLDPADB", repo).start(step).build();
  }

  @Bean
  Job loadpadbJob(
      JobRepository repo, PlatformTransactionManager tx, PendingAuthUnloadLoadService s) {
    Step step =
        new StepBuilder("PAUDBLOD", repo)
            .tasklet(
                (contribution, ctx) -> {
                  var p = contribution.getStepExecution().getJobParameters();
                  var in =
                      new PendingAuthUnloadLoadService.Unload(
                          Files.readAllBytes(Path.of(p.getString("roots"))),
                          Files.readAllBytes(Path.of(p.getString("children"))));
                  LOG.info("PAUDBLOD {}", s.load(in, PendingAuthSegments.ASCII));
                  return RepeatStatus.FINISHED;
                },
                tx)
            .build();
    return new JobBuilder("LOADPADB", repo).start(step).build();
  }

  @Bean
  Job dbpautp0Job(
      JobRepository repo, PlatformTransactionManager tx, PendingAuthUnloadLoadService s) {
    Step step =
        new StepBuilder("DBPAUTP0", repo)
            .tasklet(
                (contribution, ctx) -> {
                  var p = contribution.getStepExecution().getJobParameters();
                  LOG.info(
                      "DBPAUTP0 {}",
                      s.loadHdUnload(Files.readAllBytes(Path.of(p.getString("input")))));
                  return RepeatStatus.FINISHED;
                },
                tx)
            .build();
    return new JobBuilder("DBPAUTP0", repo).start(step).build();
  }

  /**
   * Publishes the root and child unloads as one unit: both are staged in unique files first, and
   * the previous root file is restored if the child file cannot be replaced.
   */
  static void publishPair(Path roots, byte[] rootData, Path children, byte[] childData)
      throws IOException {
    if (roots.toAbsolutePath().normalize().equals(children.toAbsolutePath().normalize())) {
      throw new IllegalArgumentException("roots and children must be different files");
    }
    List<Path> staged = new ArrayList<>();
    try {
      Path rootsTmp = stage(roots, rootData, staged);
      Path childrenTmp = stage(children, childData, staged);
      Path rootsBak =
          Files.isRegularFile(roots) ? stage(roots, Files.readAllBytes(roots), staged) : null;
      Files.move(rootsTmp, roots, StandardCopyOption.REPLACE_EXISTING);
      try {
        Files.move(childrenTmp, children, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        if (rootsBak != null) {
          Files.move(rootsBak, roots, StandardCopyOption.REPLACE_EXISTING);
        } else {
          Files.deleteIfExists(roots);
        }
        throw e;
      }
    } finally {
      for (Path t : staged) {
        Files.deleteIfExists(t);
      }
    }
  }

  private static Path stage(Path target, byte[] data, List<Path> staged) throws IOException {
    Path dir = target.toAbsolutePath().getParent();
    Path tmp = Files.createTempFile(dir, target.getFileName() + ".", ".tmp");
    staged.add(tmp);
    return Files.write(tmp, data);
  }
}
