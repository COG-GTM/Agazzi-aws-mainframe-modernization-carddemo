package com.carddemo.datamigration;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.datamigration.cli.MigrateDataCli;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-end {@code migrate-data} CLI on PostgreSQL 16 (Flyway V1, V3, V100+). */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataMigrationIT {

  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  @TempDir static Path out;

  static String lastOutput = "";

  static int cli(String... args) {
    List<String> a = new ArrayList<>(List.of(args));
    a.add("--spring.datasource.url=" + PG.getJdbcUrl());
    a.add("--spring.datasource.username=" + PG.getUsername());
    a.add("--spring.datasource.password=" + PG.getPassword());
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    int rc =
        MigrateDataCli.run(
            a.toArray(String[]::new), new PrintStream(buf, true, StandardCharsets.UTF_8));
    lastOutput = buf.toString(StandardCharsets.UTF_8);
    return rc;
  }

  /** {@code -Dgolden.update=true}: publishes the run reports next to the golden fixtures. */
  static void publish(Path report, String name) throws java.io.IOException {
    if (Boolean.getBoolean("golden.update")) {
      Path dir = TestPaths.golden().resolve("vsamToRelational/reports");
      Files.createDirectories(dir);
      String repo = TestPaths.modernRoot().getParent().toString() + "/";
      Files.writeString(dir.resolve(name), Files.readString(report).replace(repo, ""));
    }
  }

  static JdbcTemplate jdbc() {
    return new JdbcTemplate(
        new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword()));
  }

  static long versions() {
    Long v =
        jdbc()
            .queryForObject(
                "SELECT (SELECT SUM(version) FROM account) + (SELECT SUM(version) FROM card)"
                    + " + (SELECT SUM(version) FROM tran_cat_balance)",
                Long.class);
    return v == null ? 0 : v;
  }

  @Test
  @Order(1)
  void asciiLoadReconcilesAndIsIdempotent() throws Exception {
    Path r1 = out.resolve("ascii-1");
    assertThat(
            cli(
                "--job=vsamToRelational",
                "--source=ascii",
                "--input=" + TestPaths.appData("ASCII"),
                "--listcat=" + TestPaths.listcat(),
                "--output=" + r1))
        .as(lastOutput)
        .isZero();
    String md = Files.readString(r1.resolve("reconciliation.md"));
    assertThat(md).contains("result: **MATCH**").doesNotContain("MISMATCH");
    publish(r1.resolve("reconciliation.md"), "reconciliation-ascii.md");
    assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM account", Long.class)).isEqualTo(50);
    assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM daily_transaction", Long.class))
        .isEqualTo(300);
    long v = versions();
    Path r2 = out.resolve("ascii-2");
    assertThat(
            cli(
                "--job=vsamToRelational",
                "--input=" + TestPaths.appData("ASCII"),
                "--output=" + r2))
        .as(lastOutput)
        .isZero();
    assertThat(versions()).as("re-run changes nothing").isEqualTo(v);
    assertThat(lastOutput).contains("vsamToRelational.load=COMPLETED(w0)");
  }

  @Test
  @Order(2)
  void relationalToVsamRebuildsAsciiByteIdentical(@TempDir Path empty) throws Exception {
    Path r = out.resolve("unload-ascii");
    assertThat(
            cli(
                "--job=relationalToVsam",
                "--target=ascii",
                "--output=" + r,
                "--compare=" + TestPaths.appData("ASCII"),
                "--verify"))
        .as(lastOutput)
        .isZero();
    String md = Files.readString(r.resolve("relational-to-vsam.md"));
    assertThat(md).doesNotContain("DIFF").contains("BYTE-IDENTICAL");
    publish(r.resolve("relational-to-vsam.md"), "relational-to-vsam-ascii.md");
    try (var files = Files.list(TestPaths.appData("ASCII"))) {
      for (Path f : files.toList()) {
        assertThat(r.resolve("ascii").resolve(f.getFileName()))
            .as(f.getFileName().toString())
            .hasSameBinaryContentAs(f);
      }
    }
    Path none = out.resolve("unload-no-original");
    assertThat(
            cli(
                "--job=relationalToVsam",
                "--target=ascii",
                "--output=" + none,
                "--compare=" + empty,
                "--verify"))
        .isEqualTo(1);
    assertThat(Files.readString(none.resolve("relational-to-vsam.md"))).contains("NO ORIGINAL");
  }

  @Test
  @Order(3)
  void ebcdicAndExportLoadsReconcile() throws Exception {
    Path load = out.resolve("load-ebcdic");
    assertThat(
            cli(
                "--job=vsamToRelational",
                "--source=ebcdic",
                "--mode=replace",
                "--input=" + TestPaths.appData("EBCDIC"),
                "--output=" + load))
        .as(lastOutput)
        .isZero();
    assertThat(Files.readString(load.resolve("reconciliation.md"))).contains("result: **MATCH**");
    publish(load.resolve("reconciliation.md"), "reconciliation-ebcdic.md");
    Path r = out.resolve("unload-ebcdic");
    assertThat(
            cli(
                "--job=relationalToVsam",
                "--target=ebcdic",
                "--output=" + r,
                "--compare=" + TestPaths.appData("EBCDIC"),
                "--verify"))
        .as(lastOutput)
        .isZero();
    assertThat(Files.readString(r.resolve("relational-to-vsam.md")))
        .contains("BYTE-IDENTICAL")
        .doesNotContain("DIFF");
    publish(r.resolve("relational-to-vsam.md"), "relational-to-vsam-ebcdic.md");

    Path export = out.resolve("load-export");
    assertThat(
            cli(
                "--job=vsamToRelational",
                "--source=export",
                "--mode=replace",
                "--input=" + TestPaths.appData("EBCDIC"),
                "--output=" + export))
        .as(lastOutput)
        .isZero();
    assertThat(Files.readString(export.resolve("reconciliation.md")))
        .contains("result: **MATCH**")
        .contains("LOW_VALUES");
    publish(export.resolve("reconciliation.md"), "reconciliation-export.md");
    assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM transaction", Long.class))
        .isEqualTo(300);
  }

  @Test
  @Order(4)
  void validationErrorsAbortWithoutLoading(@TempDir Path bad) throws Exception {
    List<String> lines =
        new ArrayList<>(
            Files.readAllLines(
                TestPaths.appData("ASCII").resolve("cardxref.txt"), StandardCharsets.US_ASCII));
    lines.add(lines.get(0));
    Files.write(bad.resolve("cardxref.txt"), lines, StandardCharsets.US_ASCII);
    long before = jdbc().queryForObject("SELECT COUNT(*) FROM card_xref", Long.class);
    Path r = out.resolve("bad");
    assertThat(cli("--job=vsamToRelational", "--input=" + bad, "--output=" + r))
        .as(lastOutput)
        .isEqualTo(1);
    assertThat(Files.readString(r.resolve("reconciliation.md")))
        .contains("DUPLICATE_KEY")
        .contains("result: **ABORTED**");
    assertThat(jdbc().queryForObject("SELECT COUNT(*) FROM card_xref", Long.class))
        .isEqualTo(before);
    assertThat(cli("--job=vsamToRelational", "--input=" + bad, "--on-error=skip", "--output=" + r))
        .as(lastOutput)
        .isZero();
    assertThat(
            cli(
                "--job=vsamToRelational",
                "--datasets=CARDXREF,CARDDATA",
                "--input=" + bad,
                "--output=" + r))
        .isEqualTo(1);
    assertThat(lastOutput).contains("CARDDATA");
  }

  @Test
  @Order(5)
  void usrsecBcryptMigrationIsReversible() {
    cli(
        "--job=vsamToRelational",
        "--source=ebcdic",
        "--datasets=USRSEC",
        "--input=" + TestPaths.appData("EBCDIC"),
        "--output=" + out.resolve("usrsec"));
    List<Map<String, Object>> legacy =
        jdbc().queryForList("SELECT sec_usr_id, sec_usr_pwd FROM app_user ORDER BY sec_usr_id");
    assertThat(legacy).isNotEmpty();
    assertThat(cli("--job=usrsecToBcrypt")).as(lastOutput).isZero();
    BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    for (Map<String, Object> u : legacy) {
      String hash =
          jdbc()
              .queryForObject(
                  "SELECT sec_usr_pwd FROM app_user WHERE sec_usr_id = ?",
                  String.class,
                  u.get("sec_usr_id"));
      assertThat(hash).startsWith("{bcrypt}$2");
      assertThat(bcrypt.matches(((String) u.get("sec_usr_pwd")).stripTrailing(), hash.substring(8)))
          .isTrue();
    }
    assertThat(cli("--job=usrsecToBcrypt")).isZero();
    assertThat(lastOutput).contains("(w0)");
    String[] usrsecImport = {
      "--job=vsamToRelational",
      "--source=ebcdic",
      "--datasets=USRSEC",
      "--input=" + TestPaths.appData("EBCDIC"),
      "--output=" + out.resolve("usrsec2")
    };
    assertThat(cli(usrsecImport)).isEqualTo(1);
    assertThat(lastOutput).contains("usrsecRollback");
    Object changed = legacy.get(0).get("sec_usr_id");
    jdbc()
        .update(
            "UPDATE app_user SET sec_usr_pwd = '{bcrypt}changed' WHERE sec_usr_id = ?", changed);
    assertThat(cli("--job=usrsecRollback")).as(lastOutput).isZero();
    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT sec_usr_pwd FROM app_user WHERE sec_usr_id = ?", String.class, changed))
        .isEqualTo("{bcrypt}changed");
    jdbc()
        .update(
            "UPDATE app_user SET sec_usr_pwd = ? WHERE sec_usr_id = ?",
            legacy.get(0).get("sec_usr_pwd"),
            changed);
    assertThat(
            jdbc().queryForList("SELECT sec_usr_id, sec_usr_pwd FROM app_user ORDER BY sec_usr_id"))
        .isEqualTo(legacy);
    assertThat(cli(usrsecImport)).as(lastOutput).isZero();
  }

  @Test
  @Order(6)
  void cutoverConstraintsApplyOnCleanData() {
    assertThat(
            cli(
                "--job=vsamToRelational",
                "--mode=replace",
                "--input=" + TestPaths.appData("ASCII"),
                "--output=" + out.resolve("final")))
        .as(lastOutput)
        .isZero();
    assertThat(cli("--job=applyConstraints")).as(lastOutput).isZero();
    assertThat(
            jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conname LIKE 'fk\\_%'", Long.class))
        .isEqualTo(6);
    assertThat(cli("--job=nope")).isEqualTo(2);
  }
}
