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
    List<Dataset> missing = List.of();
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
    List<Dataset> missing =
        o.datasets().stream().filter(ds -> !run.sources.containsKey(ds)).toList();
    if (o.explicitDatasets() && !missing.isEmpty()) {
      throw new IllegalArgumentException(
          "requested dataset(s) without "
              + o.source()
              + " extract in "
              + o.input()
              + ": "
              + missing);
    }
    run.missing = missing;
    if (run.sources.containsKey(Dataset.USRSEC) && store.hashedPasswords() > 0) {
      throw new IllegalStateException(
          "USRSEC cutover active (app_user holds {bcrypt} passwords): run usrsecRollback before"
              + " re-importing USRSEC");
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

  /** ERROR finding on a decoded record (DECODE errors have no record to skip). */
  static boolean rejects(Violation v) {
    return v.severity() == Violation.Severity.ERROR && v.rule() != Violation.Rule.DECODE;
  }

  /** Loads all extracts in one transaction (parents first); rejected records are skipped. */
  public Map<Dataset, Integer> load(Run run, MigrationOptions o, long jobExecutionId) {
    Map<Dataset, Set<Long>> rejected =
        run.violations.stream()
            .filter(DataMigrationService::rejects)
            .collect(
                Collectors.groupingBy(
                    Violation::dataset,
                    Collectors.mapping(Violation::ordinal, Collectors.toSet())));
    tx.executeWithoutResult(
        s -> {
          if (run.sources.containsKey(Dataset.USRSEC)) {
            store.lockUsrsec();
            if (store.hashedPasswords() > 0) {
              throw new IllegalStateException(
                  "USRSEC cutover active (app_user holds {bcrypt} passwords): run usrsecRollback"
                      + " before re-importing USRSEC");
            }
          }
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
              .filter(v -> v.dataset() == ds && rejects(v))
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
            label(run, o), clock.cobolTimestamp(), entries, run.violations, false);
    run.report.write(o.output());
    return run.report;
  }

  private static String label(Run run, MigrationOptions o) {
    return o.source()
        + ":"
        + o.input()
        + (run.missing.isEmpty() ? "" : " (no extract for " + run.missing + ")");
  }

  /** Empty selected tables are not exported and any stale file of a previous run is removed. */
  public List<VsamExporter.Unloaded> unload(Run run, MigrationOptions o) {
    Path dir = o.output().resolve(o.target().name().toLowerCase(java.util.Locale.ROOT));
    for (Dataset ds : o.datasets()) {
      if (store.count(ds) > 0) {
        run.unloaded.add(exporter.unload(ds, o.target(), dir));
      } else {
        try {
          Files.deleteIfExists(dir.resolve(VsamExporter.exportFileName(ds, o.target())));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }
    return run.unloaded;
  }

  private static Path original(Dataset ds, MigrationOptions o) {
    Path expected = o.compare().resolve(VsamExporter.exportFileName(ds, o.target()));
    String original = ds.fileName(o.target());
    if (!Files.isRegularFile(expected) && original != null) {
      expected = o.compare().resolve(original);
    }
    return Files.isRegularFile(expected) ? expected : null;
  }

  /**
   * Byte comparison with {@code --compare}; writes relational-to-vsam.md. An exported file without
   * original, or an original whose table is empty, is a non-identical comparison.
   */
  public List<VsamExporter.Comparison> compare(Run run, MigrationOptions o) {
    List<VsamExporter.Comparison> out = new ArrayList<>();
    if (o.compare() == null) {
      return out;
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
    for (Dataset ds : o.datasets()) {
      VsamExporter.Unloaded u =
          run.unloaded.stream().filter(x -> x.dataset() == ds).findFirst().orElse(null);
      Path expected = original(ds, o);
      if (u == null && expected == null) {
        continue;
      }
      VsamExporter.Comparison c;
      String result;
      if (u == null) {
        long size = size(expected);
        c = new VsamExporter.Comparison(ds, expected.getFileName().toString(), false, 0, size, 0);
        result = "NOT EXPORTED (empty table)";
      } else if (expected == null) {
        c = new VsamExporter.Comparison(ds, "", false, 0, 0, size(u.file()));
        result = "NO ORIGINAL";
      } else {
        c = VsamExporter.compare(ds, u.file(), expected);
        result = c.identical() ? "BYTE-IDENTICAL" : "DIFF at byte " + c.firstDiff();
      }
      out.add(c);
      sb.append("| ")
          .append(ds)
          .append(" | `")
          .append(u == null ? c.file() : u.file().getFileName())
          .append("` | ")
          .append(u == null ? 0 : u.records())
          .append(" | ")
          .append(c.expectedSize())
          .append(" | ")
          .append(c.actualSize())
          .append(" | ")
          .append(result)
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

  private static long size(Path p) {
    try {
      return Files.size(p);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Report of a run stopped by validation errors (no DB comparison, nothing loaded). */
  public void reconcileFailed(Run run, MigrationOptions o) {
    List<ReconciliationReport.Entry> entries = new ArrayList<>();
    Map<Dataset, Long> catalog = ListcatCatalog.recordTotals(o.listcat());
    for (SourceDataset src : run.sources.values()) {
      Dataset ds = src.dataset();
      Map<String, BigDecimal> dbSums = new LinkedHashMap<>();
      for (String f : ds.checksumFields()) {
        dbSums.put(f, store.sum(ds, ds.mapper().field(f).column()));
      }
      entries.add(
          new ReconciliationReport.Entry(
              ds,
              src.file(),
              catalog.get(ds),
              DatasetMetrics.of(ds, src.records()),
              DatasetMetrics.of(ds, store.readAll(ds)),
              store.count(ds),
              dbSums,
              0));
    }
    run.report =
        new ReconciliationReport(
            label(run, o) + " (ABORTED: validation errors, nothing loaded)",
            clock.cobolTimestamp(),
            entries,
            run.violations,
            true);
    run.report.write(o.output());
  }
}
