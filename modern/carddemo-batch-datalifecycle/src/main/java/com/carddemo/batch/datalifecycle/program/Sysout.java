package com.carddemo.batch.datalifecycle.program;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** SYSOUT of a program step: DISPLAY lines as ISO-8859-1 bytes terminated by LF. */
public final class Sysout {

  private final ByteArrayOutputStream out = new ByteArrayOutputStream();

  public Sysout display(String line) {
    return display(line.getBytes(StandardCharsets.ISO_8859_1));
  }

  public Sysout display(byte[] raw) {
    out.writeBytes(raw);
    out.write('\n');
    return this;
  }

  public Sysout display(String prefix, byte[] raw) {
    out.writeBytes(prefix.getBytes(StandardCharsets.ISO_8859_1));
    return display(raw);
  }

  public byte[] bytes() {
    return out.toByteArray();
  }

  public String text() {
    return out.toString(StandardCharsets.ISO_8859_1);
  }
}
