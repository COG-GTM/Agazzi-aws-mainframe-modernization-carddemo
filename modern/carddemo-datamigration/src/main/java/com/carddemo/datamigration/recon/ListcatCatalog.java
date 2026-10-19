package com.carddemo.datamigration.recon;

import com.carddemo.datamigration.model.Dataset;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** REC-TOTAL of the data component of each KSDS cluster in {@code app/catlg/LISTCAT.txt}. */
public final class ListcatCatalog {

  private static final Pattern CLUSTER =
      Pattern.compile("^0CLUSTER -+ AWS\\.M2\\.CARDDEMO\\.(\\w+)\\.");
  private static final Pattern REC_TOTAL = Pattern.compile("REC-TOTAL-+(\\d+)");

  private ListcatCatalog() {}

  public static Map<Dataset, Long> recordTotals(Path listcat) {
    Map<Dataset, Long> out = new EnumMap<>(Dataset.class);
    if (listcat == null || !Files.isRegularFile(listcat)) {
      return out;
    }
    List<String> lines;
    try {
      lines = Files.readAllLines(listcat);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    Dataset current = null;
    for (String line : lines) {
      Matcher c = CLUSTER.matcher(line);
      if (c.find()) {
        current = byCluster(c.group(1));
        continue;
      }
      Matcher t = REC_TOTAL.matcher(line);
      if (current != null && t.find()) {
        out.put(current, Long.parseLong(t.group(1)));
        current = null;
      }
    }
    return out;
  }

  private static Dataset byCluster(String name) {
    for (Dataset d : Dataset.values()) {
      if (d.name().equals(name)) {
        return d;
      }
    }
    return null;
  }
}
