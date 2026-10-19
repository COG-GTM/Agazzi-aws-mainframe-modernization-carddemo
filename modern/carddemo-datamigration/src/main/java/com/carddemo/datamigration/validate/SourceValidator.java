package com.carddemo.datamigration.validate;

import com.carddemo.datamigration.io.SourceDataset;
import com.carddemo.datamigration.io.SourceRecord;
import com.carddemo.datamigration.model.Dataset;
import com.carddemo.datamigration.model.DateRule;
import com.carddemo.datamigration.model.Organization;
import com.carddemo.datamigration.model.RecordMapper;
import com.carddemo.datamigration.validate.Violation.Rule;
import com.carddemo.datamigration.validate.Violation.Severity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Duplicate keys, orphan logical FKs and invalid dates on decoded extracts. */
public final class SourceValidator {

  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TIMESTAMP =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSSSS")
          .withResolverStyle(ResolverStyle.STRICT);

  public List<Violation> validate(Map<Dataset, SourceDataset> sources, KeyLookup db) {
    List<Violation> out = new ArrayList<>();
    for (SourceDataset src : sources.values()) {
      for (SourceDataset.DecodeError e : src.decodeErrors()) {
        out.add(
            new Violation(
                src.dataset(), e.ordinal(), Rule.DECODE, Severity.ERROR, "", e.message()));
      }
      for (SourceDataset.DecodeError e : src.lowValues()) {
        out.add(
            new Violation(
                src.dataset(), e.ordinal(), Rule.LOW_VALUES, Severity.WARN, "", e.message()));
      }
      duplicates(src, out);
      dates(src, out);
    }
    Map<String, Set<String>> parentKeys = new HashMap<>();
    for (ForeignKey fk : ForeignKey.ALL) {
      SourceDataset child = sources.get(fk.child());
      if (child == null) {
        continue;
      }
      Optional<Set<String>> parents =
          Optional.ofNullable(
              parentKeys.computeIfAbsent(
                  fk.parent() + ":" + fk.parentColumns(),
                  k ->
                      sources.containsKey(fk.parent())
                          ? keys(sources.get(fk.parent()), fk.parentColumns())
                          : db.keys(fk.parent(), fk.parentColumns()).orElse(null)));
      if (parents.isEmpty()) {
        continue;
      }
      RecordMapper<Record> m = fk.child().mapper();
      for (SourceRecord r : child.records()) {
        String key = key(m, r.value(), fk.childColumns());
        if (fk.blankAllowed() && key.isBlank()) {
          continue;
        }
        if (!parents.get().contains(key)) {
          out.add(
              new Violation(
                  fk.child(),
                  r.ordinal(),
                  Rule.ORPHAN_FK,
                  fk.severity(),
                  String.join(",", fk.childColumns()),
                  "'" + key.trim() + "' not found: " + fk.describe()));
        }
      }
    }
    return out;
  }

  private static void duplicates(SourceDataset src, List<Violation> out) {
    Dataset ds = src.dataset();
    List<String> cols = ds.keyColumns();
    if (cols.isEmpty()) {
      return;
    }
    Severity sev = ds.organization() == Organization.KSDS ? Severity.ERROR : Severity.WARN;
    RecordMapper<Record> m = ds.mapper();
    Map<String, Long> seen = new HashMap<>();
    for (SourceRecord r : src.records()) {
      String key = key(m, r.value(), cols);
      Long first = seen.putIfAbsent(key, r.ordinal());
      if (first != null) {
        out.add(
            new Violation(
                ds,
                r.ordinal(),
                Rule.DUPLICATE_KEY,
                sev,
                String.join(",", cols),
                "key '" + key.trim() + "' already at record " + first));
      }
    }
  }

  private static void dates(SourceDataset src, List<Violation> out) {
    Dataset ds = src.dataset();
    RecordMapper<Record> m = ds.mapper();
    for (DateRule rule : ds.dateRules()) {
      var f = m.field(rule.cobolName());
      for (SourceRecord r : src.records()) {
        String v = String.valueOf(m.value(r.value(), f)).trim();
        if (v.isEmpty() && rule.blankAllowed()) {
          continue;
        }
        if (!validDate(v, rule.kind())) {
          out.add(
              new Violation(
                  ds,
                  r.ordinal(),
                  Rule.INVALID_DATE,
                  Severity.ERROR,
                  rule.cobolName(),
                  "'" + v + "' is not a valid " + rule.kind()));
        }
      }
    }
  }

  static boolean validDate(String v, DateRule.Kind kind) {
    try {
      if (kind == DateRule.Kind.DATE) {
        LocalDate.parse(v, DATE);
      } else {
        LocalDateTime.parse(v, TIMESTAMP);
      }
      return true;
    } catch (DateTimeParseException e) {
      return false;
    }
  }

  static Set<String> keys(SourceDataset src, List<String> cols) {
    RecordMapper<Record> m = src.dataset().mapper();
    Set<String> out = new HashSet<>();
    for (SourceRecord r : src.records()) {
      out.add(key(m, r.value(), cols));
    }
    return out;
  }

  /** Canonical composite key (values joined by '|'). */
  public static String key(RecordMapper<Record> m, Record r, List<String> cols) {
    StringBuilder sb = new StringBuilder();
    for (String c : cols) {
      if (!sb.isEmpty()) {
        sb.append('|');
      }
      sb.append(RecordMapper.canonical(m.value(r, m.field(c))));
    }
    return sb.toString();
  }
}
