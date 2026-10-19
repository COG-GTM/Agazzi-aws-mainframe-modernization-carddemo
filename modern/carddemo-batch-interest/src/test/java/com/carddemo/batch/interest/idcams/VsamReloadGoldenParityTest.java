package com.carddemo.batch.interest.idcams;

import static com.carddemo.batch.interest.testsupport.RecordAssert.assertRecords;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.interest.B2TestApplication;
import com.carddemo.batch.interest.support.ReturnCodes;
import com.carddemo.batch.interest.testsupport.FixtureDb;
import com.carddemo.batch.interest.testsupport.FixtureDb.Table;
import com.carddemo.batch.interest.testsupport.GoldenFixture;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** TCATBALF / DISCGRP parity gate: IDCAMS REPRO PS → KSDS vs the golden KSDS unload. */
@Tag("parity")
@SpringBootTest(classes = B2TestApplication.class)
class VsamReloadGoldenParityTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired ApplicationContext context;

  static Stream<Arguments> jobs() {
    return Stream.of(
        Arguments.of(
            "tcatbalfJob", "b2-interest/TCATBALF", "TCATBAL", "TCATBALV", Table.TRAN_CAT_BALANCE),
        Arguments.of(
            "discgrpJob", "b2-interest/DISCGRP", "DISCGRP", "DISCVSAM", Table.DISCLOSURE_GROUP));
  }

  @ParameterizedTest
  @MethodSource("jobs")
  void reproMatchesGolden(String bean, String fixture, String in, String out, Table table)
      throws Exception {
    GoldenFixture g = GoldenFixture.load(fixture);
    FixtureDb db = new FixtureDb(jdbc);
    db.clear();
    // A previous cluster content that STEP05 DELETE must drop.
    db.load(table, g.expected(out, 50).subList(0, 1));

    JobExecution ex =
        launcher.run(
            context.getBean(bean, Job.class),
            new JobParametersBuilder()
                .addString(in, g.inputPath(in).toString())
                .addLong("run", System.nanoTime())
                .toJobParameters());

    assertThat(ex.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    List<String> rcs =
        ex.getStepExecutions().stream()
            .map(s -> s.getStepName() + " " + ReturnCodes.of(s.getExitStatus()))
            .toList();
    assertThat(rcs).containsExactly("STEP05 0", "STEP10 0", "STEP15 0");
    assertThat(rcs).containsAll(g.returnCodes());
    assertRecords(table.codec(), g.expected(out, 50), db.unload(table));
  }
}
