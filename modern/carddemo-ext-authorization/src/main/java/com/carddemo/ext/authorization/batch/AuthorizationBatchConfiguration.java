package com.carddemo.ext.authorization.batch;

import com.carddemo.ext.authorization.codec.PendingAuthSegments;
import com.carddemo.ext.authorization.config.ConditionalOnAuthorizationExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
                  Path roots = Path.of(p.getString("roots"));
                  Path children = Path.of(p.getString("children"));
                  Path rootsTmp = Files.write(sibling(roots), u.roots());
                  Path childrenTmp = Files.write(sibling(children), u.children());
                  Files.move(childrenTmp, children, StandardCopyOption.REPLACE_EXISTING);
                  Files.move(rootsTmp, roots, StandardCopyOption.REPLACE_EXISTING);
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

  /** Both segment files are written completely before either replaces the previous unload. */
  private static Path sibling(Path target) {
    return target.resolveSibling(target.getFileName() + ".tmp");
  }
}
