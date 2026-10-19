package com.carddemo.ext.trantype.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import com.carddemo.ext.trantype.batch.TranTypeConsistencyCheck;
import com.carddemo.ext.trantype.support.GoldenScenarios;
import com.carddemo.ext.trantype.support.InMemoryTransactionTypeStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Flag ON over PostgreSQL 16: online goldens with real JPA/JDBC, MNTTRDB2 and TRANEXTR jobs. */
@SpringBootTest(
    classes = TrantypeItApplication.class,
    properties = {
      "carddemo.extensions.trantype.enabled=true",
      "spring.flyway.locations=classpath:db/migration,classpath:db/seed",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.batch.job.enabled=false",
      "spring.batch.jdbc.initialize-schema=never"
    })
@AutoConfigureMockMvc
class TrantypeEnabledIT extends PostgresSupport {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JobLauncher launcher;
  @Autowired TranTypeConsistencyCheck check;

  @Autowired
  @Qualifier("mnttrdb2Job")
  Job mnttrdb2;

  @Autowired
  @Qualifier("tranextrJob")
  Job tranextr;

  @TempDir Path tmp;

  private Map<String, String> types() {
    Map<String, String> m = new LinkedHashMap<>();
    jdbc.query(
        "SELECT tran_type, tran_type_desc FROM transaction_type ORDER BY tran_type",
        rs -> {
          m.put(rs.getString(1), rs.getString(2));
        });
    return m;
  }

  private void reseed() {
    jdbc.update("DELETE FROM transaction_type");
    InMemoryTransactionTypeStore.seeded()
        .types
        .forEach(
            (k, v) ->
                jdbc.update(
                    "INSERT INTO transaction_type (tran_type, tran_type_desc, filler)"
                        + " VALUES (?, ?, '00000000')",
                    k,
                    v));
  }

  @TestFactory
  Stream<DynamicTest> onlineGoldensOnPostgres() {
    return Stream.of("CTLI", "CTTU")
        .flatMap(t -> GoldenScenarios.online(t).stream())
        .map(
            p ->
                DynamicTest.dynamicTest(
                    p.getFileName().toString(),
                    () -> {
                      reseed();
                      String who = p.getParent().getFileName() + "-" + p.getFileName();
                      List<String> mismatches =
                          GoldenScenarios.run(
                              mvc, p, r -> r.with(user(who)).with(csrf()), this::types);
                      assertThat(mismatches).as(p.toString()).isEmpty();
                    }));
  }

  @Test
  void mnttrdb2JobMatchesCobolGolden() throws Exception {
    reseed();
    Path dir = GoldenScenarios.ROOT.resolve("MNTTRDB2");
    Path sysout = tmp.resolve("SYSOUT");
    JobExecution ex =
        launcher.run(
            mnttrdb2,
            new JobParametersBuilder()
                .addString("INPFILE", dir.resolve("INPFILE").toAbsolutePath().toString())
                .addString("SYSOUT", sysout.toString())
                .addLong("run", System.nanoTime())
                .toJobParameters());
    assertThat(Files.readAllLines(sysout)).isEqualTo(Files.readAllLines(dir.resolve("SYSOUT")));
    assertThat(ex.getStepExecutions().iterator().next().getExitStatus().getExitDescription())
        .isEqualTo("RC=000" + Files.readString(dir.resolve("RC")).strip());
    assertThat(check.expectedTypeExtract())
        .isEqualTo(Files.readAllLines(dir.resolve("TRANTYPE.after")));

    Path tt = Files.write(tmp.resolve("TRANTYPE"), check.expectedTypeExtract());
    Path tc = Files.write(tmp.resolve("TRANCATG"), check.expectedCategoryExtract());
    JobExecution chk =
        launcher.run(
            tranextr,
            new JobParametersBuilder()
                .addString("TRANTYPE", tt.toString())
                .addString("TRANCATG", tc.toString())
                .addLong("run", System.nanoTime())
                .toJobParameters());
    assertThat(chk.getStepExecutions().iterator().next().getExitStatus().getExitDescription())
        .isEqualTo("RC=0000");

    JobExecution missing =
        launcher.run(
            mnttrdb2,
            new JobParametersBuilder()
                .addString("INPFILE", tmp.resolve("nope").toString())
                .addLong("run", System.nanoTime())
                .toJobParameters());
    assertThat(missing.getStatus()).isEqualTo(org.springframework.batch.core.BatchStatus.FAILED);
    assertThat(missing.getExitStatus().getExitCode()).isEqualTo("FAILED");
    assertThat(missing.getStepExecutions().iterator().next().getExitStatus().getExitDescription())
        .isEqualTo("RC=0012");
    reseed();
  }
}
