package com.carddemo.datamigration.service;

import com.carddemo.contracts.port.BusinessClock;
import com.carddemo.datamigration.export.VsamExporter;
import com.carddemo.datamigration.io.SourceDataset;
import com.carddemo.datamigration.io.SourceReader;
import com.carddemo.datamigration.io.SourceRecord;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.recon.DatasetMetrics;
import com.carddemo.datamigration.recon.ListcatCatalog;
import com.carddemo.datamigration.recon.ReconciliationReport;
import com.carddemo.datamigration.store.RelationalStore;
import com.carddemo.datamigration.validate.SourceValidator;
import com.carddemo.datamigration.validate.Violation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.transaction.support.TransactionTemplate;

/** The steps of {@code vsamToRelational} and {@code relationalToVsam}, independent of Batch. */
public class DataMigrationService {

  /** State shared by the steps of one job execution. */
  public static final class Run {
    Map<Dataset, SourceDataset> sources = Map.of();
    List<Violation> violations = List.of();
    final Map<Dataset, Integer> changed = new LinkedHashMap<>();
    final List<VsamExporter.Unloaded> unloaded = new ArrayList<>();
    ReconciliationReport report;

    public Map<Dataset, SourceDataset> sources() {
      return sources;
    }

    public List<Violation> violations() {
      return violations;
    }

    public Map<Dataset, Integer> changed() {
      return changed;
    }

    public ReconciliationReport report() {
      return report;
    }
  }

  private final SourceReader reader = new SourceReader();
  private final SourceValidator validator = new SourceValidator();
  private final RelationalStore store;
  private final VsamExporter exporter;
  private final TransactionTemplate tx;
  private final BusinessClock clock;
  private final Map<Long, Run> runs = new ConcurrentHashMap<>();

  public DataMigrationService(RelationalStore store, TransactionTemplate tx, BusinessClock clock) {
    this.store = store;
    this.exporter = new VsamExporter(store);
    this.tx = tx;
    this.clock = clock;
  }

  public Run run(long jobExecutionId) {
    return runs.computeIfAbsent(jobExecutionId, k -> new Run());
  }

  public Run finish(long jobExecutionId) {
    return runs.remove(jobExecutionId);
  }

  public Map<Dataset, SourceDataset> extract(Run run, MigrationOptions o) {
    run.sources = reader.readAll(o.input(), o.source(), o.datasets());
    if (run.sources.isEmpty()) {
      throw new IllegalArgumentException(
          "no " + o.source() + " extract found in " + o.input().toAbsolutePath());
    }
    return run.sources;
  }

  public List<Violation> validate(Run run, MigrationOptions o) {
    run.violations = validator.validate(run.sources, store);
    return run.violations;
  }

  public long errorCount(Run run) {
    return run.violations.stream().filter(v -> v.severity() == Violation.Severity.ERROR).count();
  }

  /** Loads all extracts in one transaction (parents first); rejected records are skipped. */
  public Map<Dataset, Integer> load(Run run, MigrationOptions o, long jobExecutionId) {
    Map<Dataset, Set<Long>> rejected =
        run.violations.stream()
            .filter(v -> v.severity() == Violation.Severity.ERROR)
            .collect(
                Collectors.groupingBy(
                    Violation::dataset,
                    Collectors.mapping(Violation::ordinal, Collectors.toSet())));
    tx.executeWithoutResult(
        s -> {
          if (o.mode() == MigrationOptions.Mode.REPLACE) {
            List<Dataset> rev = new ArrayList<>(run.sources.keySet());
            java.util.Collections.reverse(rev);
            rev.forEach(store::deleteAll);
          }
          for (SourceDataset src : run.sources.values()) {
            Set<Long> skip = rejected.getOrDefault(src.dataset(), Set.of());
            List<SourceRecord> ok =
                src.records().stream().filter(r -> !skip.contains(r.ordinal())).toList();
            run.changed.put(src.dataset(), store.upsert(src.dataset(), ok));
            store.syncIdentity(src.dataset());
            store.saveLayout(src, jobExecutionId, clock.now().toInstant(ZoneOffset.UTC));
          }
        });
    return run.changed;
  }

  /** Source vs DB metrics; writes reconciliation.md/json into the output directory. */
  public ReconciliationReport reconcile(Run run, MigrationOptions o, long jobExecutionId) {
    Map<Dataset, Long> catalog = ListcatCatalog.recordTotals(o.listcat());
    List<ReconciliationReport.Entry> entries = new ArrayList<>();
    for (SourceDataset src : run.sources.values()) {
      Dataset ds = src.dataset();
      Set<Long> rejected =
          run.violations.stream()
              .filter(v -> v.dataset() == ds && v.severity() == Violation.Severity.ERROR)
              .map(Violation::ordinal)
              .collect(Collectors.toSet());
      List<SourceRecord> loaded =
          src.records().stream().filter(r -> !rejected.contains(r.ordinal())).toList();
      DatasetMetrics source = DatasetMetrics.of(ds, loaded);
      DatasetMetrics target = DatasetMetrics.of(ds, store.readAll(ds));
      Map<String, BigDecimal> dbSums = new LinkedHashMap<>();
      for (String f : ds.checksumFields()) {
        dbSums.put(f, store.sum(ds, ds.mapper().field(f).column()));
      }
      ReconciliationReport.Entry e =
          new ReconciliationReport.Entry(
              ds,
              src.file(),
              catalog.get(ds),
              source,
              target,
              store.count(ds),
              dbSums,
              run.changed.getOrDefault(ds, 0));
      entries.add(e);
      store.saveReconciliation(
          jobExecutionId, ds.name(), "count", "" + source.count(), "" + e.dbCount());
      source
          .sums()
          .forEach(
              (k, v) ->
                  store.saveReconciliation(
                      jobExecutionId,
                      ds.name(),
                      k,
                      v.toPlainString(),
                      dbSums.get(k).toPlainString()));
      store.saveReconciliation(
          jobExecutionId, ds.name(), "record-hash", source.recordHash(), target.recordHash());
    }
    run.report =
        new ReconciliationReport(
            o.source() + ":" + o.input(), clock.cobolTimestamp(), entries, run.violations);
    run.report.write(o.output());
    return run.report;
  }

  public List<VsamExporter.Unloaded> unload(Run run, MigrationOptions o) {
    Path dir = o.output().resolve(o.target().name().toLowerCase(java.util.Locale.ROOT));
    for (Dataset ds : o.datasets()) {
      if (store.count(ds) > 0) {
        run.unloaded.add(exporter.unload(ds, o.target(), dir));
      }
    }
    return run.unloaded;
  }

  /** Byte comparison with {@code --compare}; writes relational-to-vsam.md. */
  public List<VsamExporter.Comparison> compare(Run run, MigrationOptions o) {
    List<VsamExporter.Comparison> out = new ArrayList<>();
    if (o.compare() == null) {
      return out;
    }
    for (VsamExporter.Unloaded u : run.unloaded) {
      Path expected = o.compare().resolve(VsamExporter.exportFileName(u.dataset(), o.target()));
      String original = u.dataset().fileName(o.target());
      if (!Files.isRegularFile(expected) && original != null) {
        expected = o.compare().resolve(original);
      }
      if (Files.isRegularFile(expected)) {
        out.add(VsamExporter.compare(u.dataset(), u.file(), expected));
      }
    }
    StringBuilder sb = new StringBuilder("# relationalToVsam parity\n\n");
    sb.append("- target: `")
        .append(o.target())
        .append("`, compared with `")
        .append(o.compare())
        .append("`\n- generated: ")
        .append(clock.cobolTimestamp())
        .append("\n\n| Dataset | File | Records | Bytes expected | Bytes actual | Result |\n")
        .append("|---|---|---:|---:|---:|---|\n");
    for (VsamExporter.Unloaded u : run.unloaded) {
      VsamExporter.Comparison c =
          out.stream().filter(x -> x.dataset() == u.dataset()).findFirst().orElse(null);
      sb.append("| ")
          .append(u.dataset())
          .append(" | `")
          .append(u.file().getFileName())
          .append("` | ")
          .append(u.records())
          .append(" | ")
          .append(c == null ? "—" : c.expectedSize())
          .append(" | ")
          .append(c == null ? "—" : c.actualSize())
          .append(" | ")
          .append(
              c == null
                  ? "no original"
                  : c.identical() ? "BYTE-IDENTICAL" : "DIFF at byte " + c.firstDiff())
          .append(" |\n");
    }
    try {
      Files.createDirectories(o.output());
      Files.writeString(o.output().resolve("relational-to-vsam.md"), sb.toString());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return out;
  }

  /** Report of a run stopped by validation errors (no DB comparison, nothing loaded). */
  public void reconcileFailed(Run run, MigrationOptions o) {
    List<ReconciliationReport.Entry> entries = new ArrayList<>();
    Map<Dataset, Long> catalog = ListcatCatalog.recordTotals(o.listcat());
    for (SourceDataset src : run.sources.values()) {
      DatasetMetrics m = DatasetMetrics.of(src.dataset(), src.records());
      entries.add(
          new ReconciliationReport.Entry(
              src.dataset(),
              src.file(),
              catalog.get(src.dataset()),
              m,
              m,
              store.count(src.dataset()),
              m.sums(),
              0));
    }
    run.report =
        new ReconciliationReport(
            o.source() + ":" + o.input() + " (ABORTED: validation errors)",
            clock.cobolTimestamp(),
            entries,
            run.violations);
    run.report.write(o.output());
  }
}
