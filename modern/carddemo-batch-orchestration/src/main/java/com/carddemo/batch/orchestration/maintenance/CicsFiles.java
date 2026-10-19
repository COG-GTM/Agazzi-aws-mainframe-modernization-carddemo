package com.carddemo.batch.orchestration.maintenance;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** The five CICS files CLOSEFIL/OPENFIL toggle with {@code CEMT SET FIL(...) CLO|OPE}. */
public final class CicsFiles {

  public static final Set<String> BATCH_WINDOW =
      Set.copyOf(
          new LinkedHashSet<>(Arrays.asList("TRANSACT", "CCXREF", "ACCTDAT", "CXACAIX", "USRSEC")));

  public static final String DEFAULT_LIST = "TRANSACT,CCXREF,ACCTDAT,CXACAIX,USRSEC";

  private CicsFiles() {}

  /** Parses the {@code files} JobParameter (comma separated, CICS names are 1..8 chars). */
  public static Set<String> parse(String list) {
    Set<String> files = new LinkedHashSet<>();
    for (String f : list.split(",", -1)) {
      String name = f.trim().toUpperCase(java.util.Locale.ROOT);
      if (!name.matches("[A-Z0-9#@$]{1,8}")) {
        throw new IllegalArgumentException("invalid CICS file name '" + f + "'");
      }
      files.add(name);
    }
    return files;
  }
}
