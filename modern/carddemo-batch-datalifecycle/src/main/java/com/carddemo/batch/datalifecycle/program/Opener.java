package com.carddemo.batch.datalifecycle.program;

import java.io.UncheckedIOException;
import java.util.function.Supplier;

/** Opens a lazily supplied file, translating open failures into a file status. */
final class Opener {

  private Opener() {}

  static <T> T open(Supplier<T> supplier) throws FileStatusException {
    try {
      return supplier.get();
    } catch (UncheckedIOException e) {
      if (e.getCause() instanceof FileStatusException fse) {
        throw fse;
      }
      throw new FileStatusException(FileStatusException.NOT_FOUND, e.getMessage(), e);
    }
  }
}
