package com.carddemo.batch.orchestration.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.batch.orchestration.jobs.WaitTaskletProbe;
import com.carddemo.batch.orchestration.maintenance.CicsFiles;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.batch.JobExecutionEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class CoreTest {

  @Test
  void exitStatusToJclConditionCode() {
    assertThat(JclReturnCodes.of(ExitStatus.COMPLETED)).isZero();
    assertThat(JclReturnCodes.of(ExitStatus.NOOP)).isZero();
    assertThat(JclReturnCodes.of(new ExitStatus("RC04"))).isEqualTo(4);
    assertThat(JclReturnCodes.of(new ExitStatus("RC12"))).isEqualTo(12);
    assertThat(JclReturnCodes.of(ExitStatus.STOPPED)).isEqualTo(8);
    assertThat(JclReturnCodes.of(ExitStatus.FAILED)).isEqualTo(12);
    assertThat(JclReturnCodes.of(new ExitStatus("ABEND-S0C7"))).isEqualTo(16);
    assertThat(JclReturnCodes.of(new ExitStatus(JclReturnCodes.NOJOB))).isEqualTo(16);
    assertThat(JclReturnCodes.of(ExitStatus.UNKNOWN)).isEqualTo(16);
    assertThat(JclReturnCodes.rcExitCode(4)).isEqualTo("RC04");
  }

  private static JobExecution execution(
      BatchStatus status, ExitStatus jobExit, ExitStatus... steps) {
    JobExecution e = new JobExecution(new JobInstance(1L, "J"), 1L, new JobParameters());
    e.setStatus(status);
    e.setExitStatus(jobExit);
    int i = 0;
    for (ExitStatus s : steps) {
      StepExecution se = e.createStepExecution("S" + i++);
      se.setExitStatus(s);
    }
    return e;
  }

  @Test
  void jobConditionCodeIsTheMaxStepCode() {
    assertThat(
            JclReturnCodes.of(
                execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, ExitStatus.COMPLETED)))
        .isZero();
    assertThat(
            JclReturnCodes.of(
                execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, new ExitStatus("RC04"))))
        .isEqualTo(4);
    assertThat(
            JclReturnCodes.of(
                execution(BatchStatus.FAILED, ExitStatus.FAILED, new ExitStatus("ABEND-U0001"))))
        .isEqualTo(16);
    assertThat(JclReturnCodes.of(execution(BatchStatus.FAILED, ExitStatus.COMPLETED)))
        .isEqualTo(12);
    assertThat(
            JclReturnCodes.of(
                execution(BatchStatus.FAILED, ExitStatus.FAILED, new ExitStatus("RC08"))))
        .as("a generic FAILED job keeps its step's RC")
        .isEqualTo(8);
    assertThat(JclReturnCodes.of(execution(BatchStatus.FAILED, ExitStatus.FAILED))).isEqualTo(12);
  }

  @Test
  void exitCodeGeneratorKeepsTheHighestCode() {
    JclExitCodeGenerator g = new JclExitCodeGenerator();
    assertThat(g.getExitCode()).isZero();
    g.onApplicationEvent(
        new JobExecutionEvent(
            execution(BatchStatus.FAILED, ExitStatus.FAILED, new ExitStatus("RC08"))));
    g.record(execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED));
    assertThat(g.getExitCode()).isEqualTo(8);
  }

  @Test
  void cliEnvironmentIsSetOnlyWhenAJobNameIsGiven() {
    var pp = new BatchCliEnvironmentPostProcessor();
    var env = new StandardEnvironment();
    pp.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.batch.job.enabled")).isNull();
    env.getPropertySources()
        .addFirst(new MapPropertySource("args", Map.of("spring.batch.job.name", "WAITSTEP")));
    pp.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.batch.job.enabled")).isEqualTo("true");
    assertThat(env.getProperty("spring.main.web-application-type")).isEqualTo("none");
    assertThat(env.getProperty("carddemo.orchestration.cli.exit")).isEqualTo("true");
    var cli = new StandardEnvironment();
    cli.getPropertySources()
        .addFirst(
            new MapPropertySource(
                "commandLineArgs",
                Map.of("spring.batch.job.name", "X", "carddemo.orchestration.cli.exit", "false")));
    cli.getPropertySources()
        .addLast(new MapPropertySource("app", Map.of("spring.batch.job.enabled", "false")));
    pp.postProcessEnvironment(cli, new SpringApplication());
    assertThat(cli.getProperty("spring.batch.job.enabled")).isEqualTo("true");
    assertThat(cli.getProperty("carddemo.orchestration.cli.exit")).isEqualTo("false");
  }

  @Test
  void cliExitTerminatesWithTheContextExitCode() {
    ConfigurableApplicationContext ctx = mock(ConfigurableApplicationContext.class);
    ExitCodeGenerator gen = () -> 12;
    when(ctx.getBeansOfType(ExitCodeGenerator.class)).thenReturn(Map.of("g", gen));
    AtomicInteger code = new AtomicInteger(-1);
    AtomicInteger drained = new AtomicInteger();
    new CliExit(code::set, drained::incrementAndGet)
        .onApplicationEvent(
            new ApplicationReadyEvent(new SpringApplication(), new String[0], ctx, null));
    assertThat(code.get()).isEqualTo(12);
    assertThat(drained.get()).isEqualTo(1);
  }

  @Test
  void datasetNamesFollowMvsRules() {
    var loc = new DatasetLocator(Path.of("root"));
    assertThat(loc.resolve("AWS.M2.CARDEMO.FTP.TEST"))
        .isEqualTo(Path.of("root", "AWS.M2.CARDEMO.FTP.TEST"));
    assertThat(loc.root()).isEqualTo(Path.of("root"));
    for (String bad : new String[] {null, "../X", "TOOLONGQUALIFIER.X", "A..B", "1ABC"}) {
      assertThatThrownBy(() -> loc.resolve(bad))
          .isInstanceOfSatisfying(
              JclStepFailure.class, f -> assertThat(f.exitCode()).isEqualTo("ABEND-S813"));
    }
  }

  @Test
  void cicsFileListParsing() {
    assertThat(CicsFiles.parse(CicsFiles.DEFAULT_LIST))
        .containsExactlyElementsOf(
            java.util.List.of("TRANSACT", "CCXREF", "ACCTDAT", "CXACAIX", "USRSEC"));
    assertThat(CicsFiles.parse(" usrsec ")).containsExactly("USRSEC");
    assertThatThrownBy(() -> CicsFiles.parse("TRANSACT,"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cobswaitSysinSemanticsMatchTheGoldenMasters() {
    assertThat(WaitTaskletProbe.centiseconds("00003600      VALUE IN CENTISECONDS"))
        .isEqualTo(3600);
    assertThat(WaitTaskletProbe.centiseconds("")).isZero();
    assertThat(WaitTaskletProbe.centiseconds("ABCDEFGH")).isZero();
    assertThat(WaitTaskletProbe.centiseconds("0036ABCD")).isZero();
    assertThat(WaitTaskletProbe.centiseconds("  36")).isEqualTo(36);
    assertThat(WaitTaskletProbe.centiseconds("-0000036")).isEqualTo(36);
    assertThat(WaitTaskletProbe.centiseconds("00003600X")).isEqualTo(3600);
    assertThat(WaitTaskletProbe.centiseconds("+")).isZero();
  }
}
