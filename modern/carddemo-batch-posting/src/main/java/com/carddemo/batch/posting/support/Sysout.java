package com.carddemo.batch.posting.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** SYSOUT of a step: the COBOL {@code DISPLAY} lines, kept in memory and logged. */
public final class Sysout {

  private static final Logger LOG = LoggerFactory.getLogger(Sysout.class);

  private final String program;
  private final List<String> lines = new ArrayList<>();

  public Sysout(String program) {
    this.program = program;
  }

  public void display(String line) {
    lines.add(line);
    LOG.debug("{} {}", program, line);
  }

  public List<String> lines() {
    return Collections.unmodifiableList(lines);
  }

  /** Appends the SYSOUT (same framing as the golden harness) to {@code target}, if given. */
  public void appendTo(Path target, String stepName, int rc) {
    if (target == null) {
      return;
    }
    List<String> out = new ArrayList<>();
    out.add("---- STEP " + stepName + " PGM=" + program);
    out.addAll(lines);
    out.add("---- RC=" + rc);
    try {
      if (target.getParent() != null) {
        Files.createDirectories(target.getParent());
      }
      Files.write(
          target,
          out,
          StandardCharsets.US_ASCII,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
