package com.carddemo.ext.trantype.batch;

import com.carddemo.ext.trantype.TrantypeEnabled;
import com.carddemo.ext.trantype.store.CategoryRow;
import com.carddemo.ext.trantype.store.Pic;
import com.carddemo.ext.trantype.store.TransactionTypeStore;
import com.carddemo.ext.trantype.store.TypeRow;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Replaces TRANEXTR. The DB is the single source of truth, so instead of unloading it to the
 * TRANTYPE/TRANCATG VSAM files this check verifies referential integrity and PIC layout and, when
 * legacy extracts are supplied, compares them with the 60-byte records TRANEXTR would write.
 */
@Component
@TrantypeEnabled
public class TranTypeConsistencyCheck {

  /** Result of one run; {@code returnCode} 0 when consistent, 4 otherwise. */
  public record Report(List<String> findings, int types, int categories) {
    public int returnCode() {
      return findings.isEmpty() ? 0 : 4;
    }
  }

  private final TransactionTypeStore store;

  public TranTypeConsistencyCheck(TransactionTypeStore store) {
    this.store = store;
  }

  /** TRANEXTR step EXTDB2TT layout: TR_TYPE + CHAR(TR_DESCRIPTION,50) + '00000000'. */
  public static String typeRecord(TypeRow t) {
    return Pic.pad(t.type(), Pic.TYPE_LEN) + Pic.pad(t.description(), Pic.DESC_LEN) + "00000000";
  }

  /** TRANEXTR step EXTDB2TC layout: type + DIGITS(cat,4) + CHAR(desc,50) + '0000'. */
  public static String categoryRecord(CategoryRow c) {
    return Pic.pad(c.type(), Pic.TYPE_LEN)
        + String.format("%04d", c.category())
        + Pic.pad(c.description(), Pic.DESC_LEN)
        + "0000";
  }

  public List<String> expectedTypeExtract() {
    return store.allTypes().stream().map(TranTypeConsistencyCheck::typeRecord).toList();
  }

  public List<String> expectedCategoryExtract() {
    return store.allCategories().stream().map(TranTypeConsistencyCheck::categoryRecord).toList();
  }

  /**
   * @param typeExtract legacy TRANTYPE records, or {@code null} to skip the comparison
   * @param categoryExtract legacy TRANCATG records, or {@code null} to skip the comparison
   */
  public Report check(List<String> typeExtract, List<String> categoryExtract) {
    List<TypeRow> types = store.allTypes();
    List<CategoryRow> cats = store.allCategories();
    List<String> findings = new ArrayList<>();
    Set<String> typeKeys = types.stream().map(TypeRow::type).collect(Collectors.toSet());
    for (TypeRow t : types) {
      if (Pic.isBlank(t.description())) {
        findings.add("TYPE " + t.type() + " BLANK DESCRIPTION");
      }
      if (t.description() != null && t.description().length() > Pic.DESC_LEN) {
        findings.add("TYPE " + t.type() + " DESCRIPTION LONGER THAN PIC X(50)");
      }
    }
    for (CategoryRow c : cats) {
      if (!typeKeys.contains(c.type())) {
        findings.add(
            "CATEGORY " + c.type() + String.format("%04d", c.category()) + " ORPHAN (NO TYPE)");
      }
      if (Pic.isBlank(c.description())) {
        findings.add(
            "CATEGORY " + c.type() + String.format("%04d", c.category()) + " BLANK DESCRIPTION");
      }
      if (c.description() != null && c.description().length() > Pic.DESC_LEN) {
        findings.add(
            "CATEGORY "
                + c.type()
                + String.format("%04d", c.category())
                + " DESCRIPTION LONGER THAN PIC X(50)");
      }
    }
    if (typeExtract != null) {
      compare(
          "TRANTYPE",
          types.stream().map(TranTypeConsistencyCheck::typeRecord).toList(),
          typeExtract,
          2,
          findings);
    }
    if (categoryExtract != null) {
      compare(
          "TRANCATG",
          cats.stream().map(TranTypeConsistencyCheck::categoryRecord).toList(),
          categoryExtract,
          6,
          findings);
    }
    return new Report(List.copyOf(findings), types.size(), cats.size());
  }

  private static void compare(
      String file, List<String> expected, List<String> actual, int keyLen, List<String> out) {
    Map<String, String> db = index(expected, keyLen);
    Map<String, String> vsam = index(actual, keyLen);
    Set<String> seen = new java.util.HashSet<>();
    for (String r : actual) {
      String k = Pic.pad(r, 60).substring(0, keyLen);
      if (!seen.add(k)) {
        out.add(file + " " + k + " DUPLICATE IN EXTRACT");
      }
    }
    Set<String> keys = new TreeSet<>(db.keySet());
    keys.addAll(vsam.keySet());
    for (String k : keys) {
      String d = db.get(k);
      String v = vsam.get(k);
      if (v == null) {
        out.add(file + " " + k + " MISSING IN EXTRACT");
      } else if (d == null) {
        out.add(file + " " + k + " NOT IN DATABASE");
      } else if (!d.equals(v)) {
        out.add(file + " " + k + " DIFFERS");
      }
    }
  }

  private static Map<String, String> index(List<String> records, int keyLen) {
    Map<String, String> m = new LinkedHashMap<>();
    for (String r : records) {
      String rec = Pic.pad(r, 60);
      m.put(rec.substring(0, keyLen), rec);
    }
    return m;
  }
}
