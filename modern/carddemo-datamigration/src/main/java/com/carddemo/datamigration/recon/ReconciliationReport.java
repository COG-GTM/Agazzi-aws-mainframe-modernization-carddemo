package com.carddemo.datamigration.recon;

import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.validate.Violation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Source vs database comparison of one {@code vsamToRelational} run. */
public record ReconciliationReport(
    String source,
    String generatedAt,
    List<Entry> datasets,
    List<Violation> violations,
    boolean aborted) {

  /** Per-dataset comparison; {@code dbCount}/{@code dbSums} come from SQL aggregates. */
  public record Entry(
      Dataset dataset,
      String file,
      Long catalogCount,
      DatasetMetrics source,
      DatasetMetrics target,
      long dbCount,
      Map<String, BigDecimal> dbSums,
      int changedRows) {

    public List<String> mismatches() {
      List<String> out = new ArrayList<>();
      if (source.count() != target.count() || source.count() != dbCount) {
        out.add("count " + source.count() + " vs " + target.count() + "/" + dbCount);
      }
      source
          .sums()
          .forEach(
              (k, v) -> {
                BigDecimal t = target.sums().get(k);
                BigDecimal d = dbSums.get(k);
                if (t == null || v.compareTo(t) != 0 || d == null || v.compareTo(d) != 0) {
                  out.add("checksum " + k + " " + v + " vs " + t + "/" + d);
                }
              });
      source
          .columnHashes()
          .forEach(
              (k, v) -> {
                if (!Objects.equals(v, target.columnHashes().get(k))) {
                  out.add("column hash " + k);
                }
              });
      if (!source.recordHash().equals(target.recordHash())) {
        out.add("record hash");
      }
      return out;
    }

    public boolean matched() {
      return mismatches().isEmpty();
    }
  }

  /** An aborted run never matches, even when the database happens to hold the same rows. */
  public boolean matched() {
    return !aborted && datasets.stream().allMatch(Entry::matched);
  }

  public void write(Path dir) {
    try {
      Files.createDirectories(dir);
      Files.writeString(dir.resolve("reconciliation.md"), markdown());
      ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
      om.writeValue(dir.resolve("reconciliation.json").toFile(), json());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Stable JSON view (no timestamps): what the golden fixtures pin. */
  public Map<String, Object> json() {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("source", source);
    root.put("matched", matched());
    root.put("aborted", aborted);
    Map<String, Object> ds = new LinkedHashMap<>();
    for (Entry e : datasets) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("file", e.file());
      m.put("table", e.dataset().table());
      m.put("count", e.source().count());
      m.put("dbCount", e.dbCount());
      m.put("catalogCount", e.catalogCount());
      Map<String, String> sums = new LinkedHashMap<>();
      e.source().sums().forEach((k, v) -> sums.put(k, v.toPlainString()));
      m.put("checksums", sums);
      m.put("recordHash", e.source().recordHash());
      m.put("columnHashes", e.source().columnHashes());
      m.put("matched", e.matched());
      m.put("mismatches", e.mismatches());
      ds.put(e.dataset().name(), m);
    }
    root.put("datasets", ds);
    root.put(
        "violations",
        violations.stream()
            .map(
                v ->
                    v.severity()
                        + " "
                        + v.dataset()
                        + "#"
                        + v.ordinal()
                        + " "
                        + v.rule()
                        + " "
                        + v.field()
                        + ": "
                        + v.message())
            .toList());
    return root;
  }

  public String markdown() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Reconciliation report — vsamToRelational\n\n");
    sb.append("- source: `").append(source).append("`\n");
    sb.append("- generated: ").append(generatedAt).append('\n');
    sb.append("- result: **")
        .append(aborted ? "ABORTED" : matched() ? "MATCH" : "MISMATCH")
        .append("**\n\n");
    sb.append("## Counts and checksums\n\n");
    sb.append(
        "| Dataset | Table | File | LISTCAT REC-TOTAL | Source | Relational (rows / SQL COUNT) |"
            + " Checksums source = DB | Changed rows | Result |\n");
    sb.append("|---|---|---|---:|---:|---:|---|---:|---|\n");
    for (Entry e : datasets) {
      StringBuilder sums = new StringBuilder();
      e.source()
          .sums()
          .forEach(
              (k, v) ->
                  sums.append('`')
                      .append(k)
                      .append("` ")
                      .append(v.toPlainString())
                      .append(" = ")
                      .append(e.dbSums().get(k) == null ? "?" : e.dbSums().get(k).toPlainString())
                      .append("<br>"));
      sb.append("| ")
          .append(e.dataset())
          .append(" | `")
          .append(e.dataset().table())
          .append("` | `")
          .append(e.file())
          .append("` | ")
          .append(e.catalogCount() == null ? "—" : e.catalogCount())
          .append(" | ")
          .append(e.source().count())
          .append(" | ")
          .append(e.target().count())
          .append(" / ")
          .append(e.dbCount())
          .append(" | ")
          .append(sums.isEmpty() ? "—" : sums)
          .append(" | ")
          .append(e.changedRows())
          .append(" | ")
          .append(e.matched() ? "MATCH" : "MISMATCH: " + String.join("; ", e.mismatches()))
          .append(" |\n");
    }
    sb.append("\n## Column hashes (SHA-256, first 16 hex, source = DB)\n\n");
    for (Entry e : datasets) {
      sb.append("### ").append(e.dataset()).append(" — record hash `");
      sb.append(e.source().recordHash(), 0, 16)
          .append("`\n\n| Field | Hash | Match |\n|---|---|---|\n");
      e.source()
          .columnHashes()
          .forEach(
              (k, v) ->
                  sb.append("| `")
                      .append(k)
                      .append("` | `")
                      .append(v, 0, 16)
                      .append("` | ")
                      .append(v.equals(e.target().columnHashes().get(k)) ? "yes" : "**NO**")
                      .append(" |\n"));
      sb.append('\n');
    }
    sb.append("## Validation findings\n\n");
    if (violations.isEmpty()) {
      sb.append("None.\n");
    } else {
      sb.append(
          "| Severity | Dataset | Record | Rule | Field | Message |\n|---|---|---:|---|---|---|\n");
      for (Violation v : violations) {
        sb.append("| ")
            .append(v.severity())
            .append(" | ")
            .append(v.dataset())
            .append(" | ")
            .append(v.ordinal())
            .append(" | ")
            .append(v.rule())
            .append(" | ")
            .append(v.field())
            .append(" | ")
            .append(v.message().replace("|", "\\|"))
            .append(" |\n");
      }
    }
    return sb.toString();
  }
}
