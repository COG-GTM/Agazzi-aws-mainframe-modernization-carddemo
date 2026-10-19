package com.carddemo.batch.interest.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@code SYSOUT DD}: COBOL {@code DISPLAY} lines of the running step. Lines are kept outside the
 * step transaction (like a spool) and appended to the optional {@code SYSOUT} job parameter file.
 */
@Component
public class Sysout {

  /** Job parameter naming the file the DISPLAY output is appended to (optional). */
  public static final String PARAMETER = "SYSOUT";

  private static final Logger LOG = LoggerFactory.getLogger("SYSOUT");

  private final List<String> lines = new ArrayList<>();

  public synchronized void display(String line) {
    lines.add(line);
    LOG.info(line);
  }

  public synchronized List<String> lines() {
    return List.copyOf(lines);
  }

  public synchronized void reset() {
    lines.clear();
  }

  /** Appends the collected lines to {@code target} (when given) and starts a new step. */
  public synchronized void flush(String target) {
    try {
      if (target != null && !target.isBlank()) {
        Path p = Path.of(target);
        if (p.getParent() != null) {
          Files.createDirectories(p.getParent());
        }
        Files.write(
            p,
            lines,
            StandardCharsets.US_ASCII,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      lines.clear();
    }
  }
}
