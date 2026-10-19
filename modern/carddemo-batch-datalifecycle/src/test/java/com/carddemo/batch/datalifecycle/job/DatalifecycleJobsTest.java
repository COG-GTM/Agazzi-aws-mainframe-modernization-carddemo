package com.carddemo.batch.datalifecycle.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.datalifecycle.support.Goldens;
import com.carddemo.contracts.port.GenerationStore;
import com.carddemo.contracts.port.MaintenanceModePort;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = JobsTestApplication.class)
@ActiveProfiles("b4test")
class DatalifecycleJobsTest {

  static final List<String> ALL_JOBS =
      List.of(
          "ACCTFILE",
          "CARDFILE",
          "XREFFILE",
          "CUSTFILE",
          "TRANFILE",
          "TRANTYPE",
          "TRANCATG",
          "DUSRSECJ",
          "DEFCUST",
          "REPTFILE",
          "ESDSRRDS",
          "DEFGDGB",
          "DEFGDGD",
          "READACCT",
          "READCARD",
          "READXREF",
          "READCUST",
          "CBEXPORT",
          "CBIMPORT");

  @Autowired ApplicationContext ctx;
  @Autowired JobLauncher launcher;
  @Autowired JdbcTemplate jdbc;
  @Autowired MaintenanceModePort maintenance;
  @Autowired GenerationStore generations;

  @TempDir Path dir;
  int run;

  @BeforeEach
  void cleanDb() {
    for (String t :
        List.of(
            "account",
            "card",
            "card_xref",
            "customer",
            "transaction",
            "transaction_type",
            "transaction_category",
            "app_user",
            "usrsec_esds",
            "usrsec_rrds",
            "dl_gdg_base")) {
      jdbc.update("DELETE FROM " + t);
    }
  }

  Job job(String name) {
    return ctx.getBeansOfType(Job.class).values().stream()
        .filter(j -> j.getName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  JobParameters params(String... kv) {
    JobParametersBuilder b =
        new JobParametersBuilder()
            .addString("datasets", dir.resolve("datasets").toString())
            .addString("sysout", dir.resolve("SYSOUT-" + (++run) + ".txt").toString())
            .addString("returnCodes", dir.resolve("RC-" + run + ".txt").toString())
            .addLong("run", (long) run);
    for (int i = 0; i < kv.length; i += 2) {
      b.addString(kv[i], kv[i + 1]);
    }
    return b.toJobParameters();
  }

  JobExecution launch(String name, JobParameters p) throws Exception {
    return launcher.run(job(name), p);
  }

  JobExecution launch(String name, String... kv) throws Exception {
    return launch(name, params(kv));
  }

  static String sysout(JobExecution e) throws IOException {
    return Files.readString(
        Path.of(e.getJobParameters().getString("sysout")), StandardCharsets.ISO_8859_1);
  }

  static Map<String, String> exits(JobExecution e) {
    Map<String, String> m = new LinkedHashMap<>();
    for (StepExecution s : e.getStepExecutions()) {
      m.put(
          s.getStepName().substring(s.getStepName().indexOf('.') + 1),
          s.getExitStatus().getExitCode());
    }
    return m;
  }

  void loadAll() throws Exception {
    assertThat(
            launch("ACCTFILE", "dd.ACCTDATA", Goldens.seed("acctdata.txt").toString()).getStatus())
        .isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            launch("CARDFILE", "dd.CARDDATA", Goldens.seed("carddata.txt").toString()).getStatus())
        .isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            launch("XREFFILE", "dd.XREFDATA", Goldens.seed("cardxref.txt").toString()).getStatus())
        .isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            launch("CUSTFILE", "dd.CUSTDATA", Goldens.seed("custdata.txt").toString()).getStatus())
        .isEqualTo(BatchStatus.COMPLETED);
    assertThat(
            launch("TRANFILE", "dd.TRANSACT", Goldens.seed("dailytran.txt").toString()).getStatus())
        .isEqualTo(BatchStatus.COMPLETED);
  }

  @Test
  void everyJclMemberIsAJobWithItsSteps() {
    assertThat(ctx.getBeansOfType(Job.class).values().stream().map(Job::getName))
        .containsExactlyInAnyOrderElementsOf(ALL_JOBS);
  }

  @Test
  void loadersFillTheTablesAndToggleCicsFiles() throws Exception {
    loadAll();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isEqualTo(50);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card", Long.class)).isEqualTo(50);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM card_xref", Long.class)).isEqualTo(50);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer", Long.class)).isEqualTo(50);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction", Long.class)).isEqualTo(300);
    assertThat(maintenance.inMaintenance()).isFalse();
    JobExecution tt = launch("TRANTYPE", "dd.TRANTYPE", Goldens.seed("trantype.txt").toString());
    JobExecution tc = launch("TRANCATG", "dd.TRANCATG", Goldens.seed("trancatg.txt").toString());
    assertThat(exits(tt)).containsOnlyKeys("STEP05", "STEP10", "STEP15");
    assertThat(LegacyExitCodes.of(tt)).isZero();
    assertThat(LegacyExitCodes.of(tc)).isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_type", Long.class))
        .isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_category", Long.class))
        .isEqualTo(18);
    // A second ACCTFILE run replaces the cluster content (DELETE/DEFINE/REPRO).
    JobExecution again = launch("ACCTFILE", "dd.ACCTDATA", Goldens.seed("acctdata.txt").toString());
    assertThat(exits(again))
        .containsExactly(
            Map.entry("STEP05", "COMPLETED"),
            Map.entry("STEP10", "COMPLETED"),
            Map.entry("STEP15", "COMPLETED"));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isEqualTo(50);
  }

  @Test
  void readJobsFromTheDatabaseMatchTheCobolGoldens() throws Exception {
    loadAll();
    JobExecution acct = launch("READACCT");
    assertThat(acct.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    Path ds = dir.resolve("datasets");
    assertThat(Files.readAllBytes(ds.resolve("AWS.M2.CARDDEMO.ACCTDATA.PSCOMP")))
        .isEqualTo(Goldens.bytes(Goldens.batch("READACCT", "PSCOMP.bin")));
    assertThat(Files.readAllBytes(ds.resolve("AWS.M2.CARDDEMO.ACCTDATA.ARRYPS")))
        .isEqualTo(Goldens.bytes(Goldens.batch("READACCT", "ARRYPS.bin")));
    assertThat(Files.readAllBytes(ds.resolve("AWS.M2.CARDDEMO.ACCTDATA.VBPS")))
        .isEqualTo(Goldens.bytes(Goldens.batch("READACCT", "VBPS.bin")));
    assertThat(sysout(acct).replace("---- STEP PREDEL PGM=IEFBR14\n---- RC=0\n", ""))
        .isEqualTo(Goldens.sysout(Goldens.batch("READACCT", "SYSOUT.txt")));
    for (String j : List.of("READCARD", "READXREF", "READCUST")) {
      assertThat(sysout(launch(j))).as(j).isEqualTo(Goldens.sysout(Goldens.b4(j, "SYSOUT.txt")));
    }
  }

  @Test
  void exportFromTheDatabaseAndImportMatchTheGoldens() throws Exception {
    loadAll();
    JobExecution exp = launch("CBEXPORT", "dd.EXPFILE", dir.resolve("EXPDATA.dat").toString());
    assertThat(exits(exp)).containsOnlyKeys("STEP01", "STEP02");
    assertThat(Files.readAllBytes(dir.resolve("EXPDATA.dat")))
        .isEqualTo(Goldens.bytes(Goldens.b4("CBEXPORT", "EXPDATA.dat")));
    assertThat(sysout(exp)).endsWith(Goldens.sysout(Goldens.b4("CBEXPORT", "SYSOUT.txt")));
    String[] kv = {"dd.EXPFILE", dir.resolve("EXPDATA.dat").toString()};
    List<String> outs = List.of("CUSTOUT", "ACCTOUT", "XREFOUT", "TRNXOUT", "CARDOUT", "ERROUT");
    String[] all = new String[kv.length + outs.size() * 2];
    System.arraycopy(kv, 0, all, 0, kv.length);
    for (int i = 0; i < outs.size(); i++) {
      all[kv.length + 2 * i] = "dd." + outs.get(i);
      all[kv.length + 2 * i + 1] = dir.resolve("imp").resolve(outs.get(i) + ".dat").toString();
    }
    JobExecution imp = launch("CBIMPORT", all);
    assertThat(imp.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    for (String o : outs) {
      assertThat(Files.readAllBytes(dir.resolve("imp").resolve(o + ".dat")))
          .as(o)
          .isEqualTo(Goldens.bytes(Goldens.b4("CBIMPORT", o + ".dat")));
    }
    assertThat(sysout(imp)).isEqualTo(Goldens.sysout(Goldens.b4("CBIMPORT", "SYSOUT.txt")));
  }

  @Test
  void userSecurityJobsLoadKsdsEsdsAndRrds() throws Exception {
    JobExecution d = launch("DUSRSECJ");
    assertThat(exits(d)).containsOnlyKeys("PREDEL", "STEP01", "STEP02", "STEP03");
    assertThat(LegacyExitCodes.of(d)).isZero();
    assertThat(
            jdbc.queryForList(
                "SELECT sec_usr_id, sec_usr_pwd, sec_usr_type FROM app_user ORDER BY sec_usr_id"))
        .hasSize(10)
        .first()
        .satisfies(
            row -> {
              assertThat(row.get("sec_usr_id")).isEqualTo("ADMIN001");
              assertThat(row.get("sec_usr_pwd")).isEqualTo("{test}PASSWORD");
              assertThat(row.get("sec_usr_type")).isEqualTo("A");
            });
    assertThat(Files.size(dir.resolve("datasets/AWS.M2.CARDDEMO.USRSEC.PS"))).isEqualTo(10 * 80);
    JobExecution e = launch("ESDSRRDS");
    assertThat(exits(e))
        .containsOnlyKeys("PREDEL", "STEP01", "STEP02", "STEP03", "STEP04", "STEP05");
    assertThat(jdbc.queryForList("SELECT rba FROM usrsec_esds ORDER BY rba", Long.class))
        .startsWith(0L, 80L, 160L)
        .hasSize(10);
    assertThat(jdbc.queryForList("SELECT rrn FROM usrsec_rrds ORDER BY rrn", Integer.class))
        .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    assertThat(
            jdbc.queryForObject("SELECT sec_usr_id FROM usrsec_rrds WHERE rrn = 6", String.class))
        .isEqualTo("USER0001");
  }

  @Test
  void gdgJobsDefineBasesAndCopyGenerations() throws Exception {
    JobExecution b = launch("DEFGDGB");
    assertThat(LegacyExitCodes.of(b)).isZero();
    assertThat(LegacyExitCodes.of(launch("DEFGDGB"))).as("IF LASTCC=12 THEN SET MAXCC=0").isZero();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM dl_gdg_base", Long.class)).isEqualTo(6);
    assertThat(LegacyExitCodes.of(launch("REPTFILE"))).as("TRANREPT already defined").isEqualTo(12);
    String[] kv = {
      "dd.STEP20.SYSUT1", Goldens.seed("trantype.txt").toString(),
      "dd.STEP40.SYSUT1", Goldens.seed("trancatg.txt").toString(),
      "dd.STEP60.SYSUT1", Goldens.seed("discgrp.txt").toString()
    };
    JobExecution d = launch("DEFGDGD", kv);
    assertThat(LegacyExitCodes.of(d)).isZero();
    assertThat(exits(d))
        .containsOnlyKeys("STEP10", "STEP20", "STEP30", "STEP40", "STEP50", "STEP60");
    var gen = generations.resolve("AWS.M2.CARDDEMO.TRANTYPE.BKUP", 0).orElseThrow();
    try (var in = generations.openInput(gen)) {
      assertThat(in.readAllBytes()).isEqualTo(Goldens.bytes(Goldens.seed("trantype.txt")));
    }
    // Re-run: the GDG bases exist (RC 12) and COND=(0,NE) bypasses the copies.
    JobExecution again = launch("DEFGDGD", kv);
    assertThat(LegacyExitCodes.of(again)).isEqualTo(12);
    assertThat(exits(again))
        .containsEntry("STEP10", "RC=12")
        .containsEntry("STEP20", "FLUSHED")
        .containsEntry("STEP60", "FLUSHED");
    assertThat(generations.list("AWS.M2.CARDDEMO.TRANTYPE.BKUP")).hasSize(1);
  }

  @Test
  void reportFileAndDefcust() throws Exception {
    assertThat(LegacyExitCodes.of(launch("REPTFILE"))).isZero();
    assertThat(jdbc.queryForObject("SELECT gen_limit FROM dl_gdg_base", Integer.class))
        .isEqualTo(10);
    JobExecution c = launch("DEFCUST");
    assertThat(exits(c))
        .containsExactly(Map.entry("STEP05", "COMPLETED"), Map.entry("STEP05B", "COMPLETED"));
  }

  @Test
  void failedStepIsRestartedFromTheFailedStep() throws Exception {
    Path ps = dir.resolve("late/trantype.txt");
    JobParameters p = params("dd.TRANTYPE", ps.toString());
    JobExecution first = launch("TRANTYPE", p);
    assertThat(first.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(exits(first)).containsEntry("STEP15", "RC=12");
    assertThat(LegacyExitCodes.of(first)).isEqualTo(12);
    assertThat(sysout(first)).contains("IEF212I TRANTYPE - DATA SET NOT FOUND");
    Files.createDirectories(ps.getParent());
    Files.copy(Goldens.seed("trantype.txt"), ps);
    JobExecution restart = launch("TRANTYPE", p);
    assertThat(restart.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(exits(restart)).containsOnlyKeys("STEP15");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_type", Long.class))
        .isEqualTo(7);
  }

  @Test
  void emptyInputEndsWithConditionCode4() throws Exception {
    Path empty = Files.createFile(dir.resolve("empty.txt"));
    JobExecution e = launch("TRANCATG", "dd.TRANCATG", empty.toString());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(exits(e)).containsEntry("STEP15", "RC=4");
    assertThat(LegacyExitCodes.of(e)).isEqualTo(4);
    assertThat(sysout(e)).contains("IDC0005I NUMBER OF RECORDS PROCESSED WAS 0");
  }

  @Test
  void malformedInputFailsTheStepAndRollsBack() throws Exception {
    Path bad = dir.resolve("bad.dat");
    Files.write(bad, new byte[] {'0', '1', '\n'});
    JobExecution e = launch("ACCTFILE", "dd.ACCTDATA", bad.toString());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(exits(e)).containsEntry("STEP15", "RC=12");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isZero();
    Path dup = dir.resolve("dup.txt");
    String line =
        Files.readAllLines(Goldens.seed("trantype.txt"), StandardCharsets.ISO_8859_1).get(0);
    Files.write(dup, List.of(line, line), StandardCharsets.ISO_8859_1);
    JobExecution d = launch("TRANTYPE", "dd.TRANTYPE", dup.toString());
    assertThat(exits(d)).containsEntry("STEP15", "RC=12");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transaction_type", Long.class)).isZero();
  }

  @Test
  void programAbendMapsToExitCode16() throws Exception {
    JobExecution e = launch("READCARD", "dd.CARDFILE", dir.resolve("missing.txt").toString());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(exits(e)).containsEntry("STEP05", "ABEND");
    assertThat(LegacyExitCodes.of(e)).isEqualTo(16);
    assertThat(sysout(e))
        .contains("ERROR OPENING CARDFILE")
        .contains("ABENDING PROGRAM")
        .endsWith("---- RC=16\n");
  }

  @Test
  void failedRefreshReopensTheCicsFiles() throws Exception {
    JobExecution e = launch("CARDFILE", "dd.CARDDATA", dir.resolve("absent.txt").toString());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.FAILED);
    assertThat(exits(e)).containsEntry("CLCIFIL", "COMPLETED").doesNotContainKey("OPCIFIL");
    assertThat(maintenance.inMaintenance()).isFalse();
  }

  @Test
  void outOfSequenceReproEndsWithRc12() throws Exception {
    List<String> lines =
        Files.readAllLines(Goldens.seed("acctdata.txt"), StandardCharsets.ISO_8859_1);
    Path rev = dir.resolve("rev.txt");
    Files.write(rev, List.of(lines.get(1), lines.get(0)), StandardCharsets.ISO_8859_1);
    JobExecution e = launch("ACCTFILE", "dd.ACCTDATA", rev.toString());
    assertThat(exits(e)).containsEntry("STEP15", "RC=12");
    assertThat(sysout(e)).contains("RECORD OUT OF SEQUENCE");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isZero();
  }

  @Test
  void cbimportWritesCardsToTheDefaultDataset() throws Exception {
    JobExecution e =
        launch("CBIMPORT", "dd.EXPFILE", Goldens.b4("CBEXPORT", "EXPDATA.dat").toString());
    assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(Files.size(dir.resolve("datasets/AWS.M2.CARDDEMO.CARDDATA.IMPORT")))
        .isEqualTo(50 * 150);
  }
}
