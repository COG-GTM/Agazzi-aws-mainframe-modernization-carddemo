package com.carddemo.batch.interest.prtcatbl;

import static com.carddemo.batch.interest.testsupport.RecordAssert.assertRecords;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.interest.B2TestApplication;
import com.carddemo.batch.interest.support.ReturnCodes;
import com.carddemo.batch.interest.testsupport.FixtureDb;
import com.carddemo.batch.interest.testsupport.FixtureDb.Table;
import com.carddemo.batch.interest.testsupport.Generations;
import com.carddemo.batch.interest.testsupport.GoldenFixture;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.copybook.CoreCodecs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** PRTCATBL parity gate: BKUP(+1) vs harness REPRO, REPT vs the DFSORT OUTREC EDIT layout. */
@Tag("parity")
@SpringBootTest(classes = B2TestApplication.class)
class PrtcatblGoldenParityTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired GenerationStore generations;

  @Autowired
  @Qualifier("prtcatblJob")
  Job job;

  @TempDir Path tmp;

  @Test
  void backupAndReportMatchGolden() throws Exception {
    GoldenFixture g = GoldenFixture.load("b2-interest/PRTCATBL");
    FixtureDb db = new FixtureDb(jdbc);
    db.clear();
    db.load(Table.TRAN_CAT_BALANCE, g.input("TCATBALF"));
    Path rept = tmp.resolve("AWS.M2.CARDDEMO.TCATBALF.REPT");
    Files.writeString(rept, "stale report deleted by DELDEF\n");

    JobExecution ex =
        launcher.run(
            job,
            new JobParametersBuilder()
                .addString(PrtcatblJobConfig.REPT_PARAMETER, rept.toString())
                .addLong("run", System.nanoTime())
                .toJobParameters());

    assertThat(ex.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    List<String> rcs =
        ex.getStepExecutions().stream()
            .map(s -> s.getStepName() + " " + ReturnCodes.of(s.getExitStatus()))
            .toList();
    assertThat(rcs).containsExactly("DELDEF 0", "STEP05R 0", "STEP10R 0");
    assertThat(rcs).containsAll(g.returnCodes());
    assertRecords(
        CoreCodecs.TRAN_CAT_BALANCE,
        g.expected("BKUP", 50),
        Generations.current(generations, PrtcatblJobConfig.BKUP));
    assertThat(Files.readAllLines(rept, StandardCharsets.US_ASCII))
        .containsExactlyElementsOf(g.expectedFile("expected-dfsort/REPT.dat"));
  }
}
