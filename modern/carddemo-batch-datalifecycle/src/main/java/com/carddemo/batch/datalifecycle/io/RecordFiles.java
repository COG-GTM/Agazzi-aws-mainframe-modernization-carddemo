package com.carddemo.batch.datalifecycle.io;

import com.carddemo.batch.datalifecycle.program.FileStatusException;
import com.carddemo.batch.datalifecycle.program.RecordSink;
import com.carddemo.batch.datalifecycle.program.RecordSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/** Fixed/variable record files used for DD allocations. */
public final class RecordFiles {

  private RecordFiles() {}

  /** Reads every record of {@code path}; malformed lengths raise file status 04. */
  public static List<byte[]> readAll(Path path, int lrecl, RecordFormat format)
      throws FileStatusException {
    byte[] data;
    try {
      data = Files.readAllBytes(path);
    } catch (NoSuchFileException e) {
      throw new FileStatusException(FileStatusException.NOT_FOUND, path.toString(), e);
    } catch (IOException e) {
      throw new FileStatusException(FileStatusException.IO_ERROR, path.toString(), e);
    }
    return parse(data, lrecl, format, path.toString());
  }

  static List<byte[]> parse(byte[] data, int lrecl, RecordFormat format, String name)
      throws FileStatusException {
    List<byte[]> out = new ArrayList<>();
    switch (format) {
      case LINES -> {
        int start = 0;
        for (int i = 0; i <= data.length; i++) {
          if (i == data.length || data[i] == '\n') {
            int end = i > start && data[i - 1] == '\r' ? i - 1 : i;
            if (i == data.length && end == start) {
              break;
            }
            if (end - start > lrecl) {
              throw wrongLength(name, out.size() + 1);
            }
            byte[] rec = new byte[lrecl];
            Arrays.fill(rec, (byte) ' ');
            System.arraycopy(data, start, rec, 0, end - start);
            out.add(rec);
            start = i + 1;
          }
        }
      }
      case FIXED_LF, FIXED -> {
        int stride = format == RecordFormat.FIXED_LF ? lrecl + 1 : lrecl;
        if (data.length % stride != 0) {
          throw wrongLength(name, data.length / stride + 1);
        }
        for (int off = 0; off < data.length; off += stride) {
          if (format == RecordFormat.FIXED_LF && data[off + lrecl] != '\n') {
            throw wrongLength(name, off / stride + 1);
          }
          out.add(Arrays.copyOfRange(data, off, off + lrecl));
        }
      }
      case VARIABLE -> {
        int off = 0;
        while (off < data.length) {
          if (off + 4 > data.length) {
            throw wrongLength(name, out.size() + 1);
          }
          int len = ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
          if (len > lrecl || off + 4 + len > data.length) {
            throw wrongLength(name, out.size() + 1);
          }
          out.add(Arrays.copyOfRange(data, off + 4, off + 4 + len));
          off += 4 + len;
        }
      }
      default -> throw new IllegalArgumentException(format.name());
    }
    return out;
  }

  private static FileStatusException wrongLength(String name, int record) {
    return new FileStatusException(
        FileStatusException.WRONG_LENGTH, name + ": invalid record length at record " + record);
  }

  /** Serialises records in the given format. */
  public static byte[] format(List<byte[]> records, RecordFormat format) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] r : records) {
      appendTo(out, r, format);
    }
    return out.toByteArray();
  }

  static void appendTo(ByteArrayOutputStream out, byte[] r, RecordFormat format) {
    if (format == RecordFormat.VARIABLE) {
      out.write(r.length >> 8);
      out.write(r.length);
      out.write(0);
      out.write(0);
    }
    if (format == RecordFormat.LINES) {
      int end = r.length;
      while (end > 0 && r[end - 1] == ' ') {
        end--;
      }
      out.write(r, 0, end);
    } else {
      out.writeBytes(r);
    }
    if (format == RecordFormat.LINES || format == RecordFormat.FIXED_LF) {
      out.write('\n');
    }
  }

  /** A sequential source over an in-memory record list. */
  public static RecordSource source(List<byte[]> records) {
    Iterator<byte[]> it = records.iterator();
    return new RecordSource() {
      @Override
      public Optional<byte[]> next() {
        return it.hasNext() ? Optional.of(it.next()) : Optional.empty();
      }

      @Override
      public void close() {}
    };
  }

  /** Opens a PS file, or a KSDS read in key order when {@code keyLength > 0}. */
  public static RecordSource open(Path path, int lrecl, int keyOffset, int keyLength) {
    try {
      List<byte[]> records = new ArrayList<>(readAll(path, lrecl, RecordFormat.of(path)));
      if (keyLength > 0) {
        records.sort(
            Comparator.comparing(
                r -> Arrays.copyOfRange(r, keyOffset, keyOffset + keyLength),
                Arrays::compareUnsigned));
      }
      return source(records);
    } catch (FileStatusException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Opens a sink that (re)creates {@code path}, format chosen from the file name. */
  public static RecordSink create(Path path) {
    return create(path, RecordFormat.of(path));
  }

  public static RecordSink create(Path path, RecordFormat format) {
    try {
      if (path.getParent() != null) {
        Files.createDirectories(path.getParent());
      }
      return sink(Files.newOutputStream(path), format);
    } catch (IOException e) {
      throw new UncheckedIOException(
          new FileStatusException(FileStatusException.IO_ERROR, path.toString(), e));
    }
  }

  /** A sink writing to {@code stream} (closed with the sink). */
  public static RecordSink sink(OutputStream stream, RecordFormat format) {
    return new RecordSink() {
      private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

      @Override
      public void write(byte[] record) throws FileStatusException {
        buffer.reset();
        appendTo(buffer, record, format);
        try {
          buffer.writeTo(stream);
        } catch (IOException e) {
          throw new FileStatusException(FileStatusException.IO_ERROR, e.getMessage(), e);
        }
      }

      @Override
      public void close() throws FileStatusException {
        try {
          stream.close();
        } catch (IOException e) {
          throw new FileStatusException(FileStatusException.IO_ERROR, e.getMessage(), e);
        }
      }
    };
  }
}
