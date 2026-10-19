package com.carddemo.contracts.port;

import java.util.Set;

/**
 * CEMT SET FILE CLOSE/OPEN (CLOSEFIL/OPENFIL jobs): while a file is closed online services treat it
 * as unavailable (read-only API). Implemented by B5, consumed by online modules.
 */
public interface MaintenanceModePort {

  /** Closes the given CICS files (DD names, e.g. ACCTDAT, CARDDAT). */
  void close(Set<String> files);

  void open(Set<String> files);

  Set<String> closedFiles();

  default boolean isOpen(String file) {
    return !closedFiles().contains(file);
  }

  default boolean inMaintenance() {
    return !closedFiles().isEmpty();
  }
}
