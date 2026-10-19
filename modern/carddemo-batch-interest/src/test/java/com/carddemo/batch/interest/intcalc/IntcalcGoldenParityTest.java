package com.carddemo.batch.interest.intcalc;

import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.ACCOUNT;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.CARD_XREF;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.DISCLOSURE_GROUP;
import static com.carddemo.batch.interest.testsupport.FixtureDb.Table.TRAN_CAT_BALANCE;
import static com.carddemo.batch.interest.testsupport.RecordAssert.assertRecords;
import static com.carddemo.batch.interest.testsupport.RecordAssert.assertSysout;
import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.interest.B2TestApplication;
import com.carddemo.batch.interest.support.ReturnCodes;
import com.carddemo.batch.interest.testsupport.FixtureDb;
import com.carddemo.batch.interest.testsupport.Generations;
import com.carddemo.batch.interest.testsupport.GoldenFixture;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.copybook.CoreCodecs;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** INTCALC parity gate: Java vs CBACT04C under GnuCOBOL on every output (file, DB, SYSOUT, RC). */
@Tag("parity")
@SpringBootTest(classes = B2TestApplication.class)
class IntcalcGoldenParityTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired GenerationStore generations;

  @Autowired
  @Qualifier("intcalcJob")
  Job job;

  @TempDir Path tmp;

  static Stream<String> fixtures() {
    return Stream.of(
        "batch/INTCALC",
        "b2-interest/INTCALC-SEED",
        "b2-interest/INTCALC-GROUPS",
        "b2-interest/INTCALC-EMPTY",
        "b2-interest/INTCALC-NODEFAULT",
        "b2-interest/INTCALC-NOACCT");
  }

  @ParameterizedTest
  @MethodSource("fixtures")
  void javaMatchesCobolGolden(String fixture) throws Exception {
    GoldenFixture g = GoldenFixture.load(fixture);
    FixtureDb db = new FixtureDb(jdbc);
    db.clear();
    db.load(ACCOUNT, g.input("ACCTDATA"));
    db.load(CARD_XREF, g.input("CARDXREF"));
    db.load(TRAN_CAT_BALANCE, g.input("TCATBALF"));
    db.load(DISCLOSURE_GROUP, g.input("DISCGRP"));
    List<String> tcatBefore = db.unload(TRAN_CAT_BALANCE);
    List<String> xrefBefore = db.unload(CARD_XREF);
    List<String> discBefore = db.unload(DISCLOSURE_GROUP);
    int generationsBefore = Generations.latest(generations, IntcalcTasklet.SYSTRAN);
    Path sysout = tmp.resolve("SYSOUT.txt");

    JobExecution ex =
        launcher.run(
            job,
            new JobParametersBuilder()
                .addString(IntcalcTasklet.PARM, "2022071800")
                .addString("SYSOUT", sysout.toString(), false)
                .addString("fixture", fixture + System.nanoTime())
                .toJobParameters());

    assertThat(
            ex.getStepExecutions().stream()
                .map(s -> s.getStepName() + " " + ReturnCodes.of(s.getExitStatus()))
                .toList())
        .isEqualTo(g.returnCodes());
    assertRecords(CoreCodecs.ACCOUNT, g.expected("ACCTDATA", 300), db.unload(ACCOUNT));
    boolean completed = ex.getStatus() == BatchStatus.COMPLETED;
    assertThat(Generations.latest(generations, IntcalcTasklet.SYSTRAN))
        .isEqualTo(generationsBefore + (completed ? 1 : 0));
    List<String> systran =
        completed ? Generations.current(generations, IntcalcTasklet.SYSTRAN) : List.of();
    assertRecords(CoreCodecs.TRANSACTION, g.expected("SYSTRAN", 350), systran);
    assertSysout(
        CoreCodecs.TRAN_CAT_BALANCE,
        g.sysout(),
        Files.readAllLines(sysout, StandardCharsets.US_ASCII));
    // DB delta: only account rows change; TCATBALF, XREF, DISCGRP and TRANSACT are untouched.
    assertThat(db.unload(TRAN_CAT_BALANCE)).isEqualTo(tcatBefore);
    assertThat(db.unload(CARD_XREF)).isEqualTo(xrefBefore);
    assertThat(db.unload(DISCLOSURE_GROUP)).isEqualTo(discBefore);
    assertThat(db.count("transaction")).isZero();
  }
}
