package com.carddemo.batch.reports.launcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.reports.B3Fixtures;
import com.carddemo.batch.reports.ReportsTestApplication;
import com.carddemo.batch.reports.support.FixedRecords;
import com.carddemo.batch.reports.support.H2Tables;
import com.carddemo.contracts.port.ReportJobLauncherPort;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportJobHandle;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportRequest;
import com.carddemo.contracts.port.ReportJobLauncherPort.ReportType;
import com.carddemo.copybook.CoreCodecs;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = ReportsTestApplication.class)
class ReportJobLauncherTest {

  @Autowired ReportJobLauncherPort port;
  @Autowired JdbcTemplate jdbc;
  @Autowired JobExplorer explorer;

  @Value("${carddemo.reports.dataset-root}")
  String root;

  @Test
  void beanReplacesFase0Fallback() {
    assertThat(port).isInstanceOf(SpringBatchReportJobLauncher.class);
  }

  @Test
  void monthlyReportRunsTranreptOnV1Tables() throws Exception {
    H2Tables.load(
        jdbc,
        "transaction",
        CoreCodecs.TRANSACTION,
        FixedRecords.read(B3Fixtures.batchGolden("COMBTRAN/expected/TRANSACT.dat"), 350));
    H2Tables.load(
        jdbc,
        "card_xref",
        CoreCodecs.CARD_XREF,
        FixedRecords.read(B3Fixtures.seed("cardxref.txt"), 50));
    H2Tables.load(
        jdbc,
        "transaction_type",
        CoreCodecs.TRANSACTION_TYPE,
        FixedRecords.read(B3Fixtures.seed("trantype.txt"), 60));
    H2Tables.load(
        jdbc,
        "transaction_category",
        CoreCodecs.TRANSACTION_CATEGORY,
        FixedRecords.read(B3Fixtures.seed("trancatg.txt"), 60));

    ReportJobHandle h = port.launch(new ReportRequest(ReportType.MONTHLY, null, null, "ADMIN001"));
    assertThat(h.jobName()).isEqualTo("TRANREPT");
    ReportJobHandle done = awaitEnd(h.executionId());
    assertThat(done.status()).isEqualTo("COMPLETED");

    var params = explorer.getJobExecution(h.executionId()).getJobParameters();
    assertThat(params.getString("startDate")).isEqualTo("2022-07-01");
    assertThat(params.getString("endDate")).isEqualTo("2022-07-31");
    Path report = Path.of(params.getString("datasetDir")).resolve("TRANREPT.dat");
    assertThat(B3Fixtures.bytes(report))
        .isEqualTo(B3Fixtures.bytes(B3Fixtures.B3.resolve("TRANREPT/expected/TRANREPT.dat")));
    assertThat(report.toString()).startsWith(Path.of(root).toString());
  }

  @Test
  void unknownExecutionHasNoStatus() {
    assertThat(port.status(987_654_321L)).isEmpty();
  }

  @Test
  void dateRangesFollowCorpt00c() {
    LocalDate today = LocalDate.of(2024, 2, 10);
    assertThat(
            SpringBatchReportJobLauncher.range(
                new ReportRequest(ReportType.MONTHLY, null, null, "u"), today))
        .containsExactly(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29));
    assertThat(
            SpringBatchReportJobLauncher.range(
                new ReportRequest(ReportType.YEARLY, null, null, "u"), today))
        .containsExactly(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    LocalDate a = LocalDate.of(2022, 7, 1);
    LocalDate b = LocalDate.of(2022, 7, 5);
    assertThat(
            SpringBatchReportJobLauncher.range(
                new ReportRequest(ReportType.CUSTOM, a, b, "u"), today))
        .containsExactly(a, b);
    assertThatThrownBy(
            () ->
                SpringBatchReportJobLauncher.range(
                    new ReportRequest(ReportType.CUSTOM, b, a, "u"), today))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                SpringBatchReportJobLauncher.range(
                    new ReportRequest(ReportType.CUSTOM, null, a, "u"), today))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> port.launch(new ReportRequest(ReportType.CUSTOM, null, null, "u")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private ReportJobHandle awaitEnd(long id) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < deadline) {
      ReportJobHandle h = port.status(id).orElseThrow();
      if (!Set.of("STARTING", "STARTED", "UNKNOWN").contains(h.status())) {
        return h;
      }
      Thread.sleep(50);
    }
    throw new AssertionError("TRANREPT did not finish");
  }
}
