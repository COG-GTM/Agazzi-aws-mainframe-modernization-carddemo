package com.carddemo.platform.files;

import com.carddemo.contracts.port.GenerationStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fase 0 GDG store on the filesystem: {@code <root>/<base>/G<nnnn>V00} files; allocated generations
 * live in {@code .new} files until committed. Retention = {@link #limit(String)}.
 */
public final class FileSystemGenerationStore implements GenerationStore {

  private static final Pattern GEN = Pattern.compile("G(\\d{4})V00");
  private final Path root;
  private final int limit;

  public FileSystemGenerationStore(Path root, int limit) {
    this.root = root;
    this.limit = limit;
  }

  public FileSystemGenerationStore(Path root) {
    this(root, 5);
  }

  private Path dir(String base) {
    return root.resolve(base);
  }

  private Path file(Generation g) {
    return dir(g.base()).resolve("G%04dV00".formatted(g.number()));
  }

  private Path pending(Generation g) {
    return dir(g.base()).resolve("G%04dV00.new".formatted(g.number()));
  }

  @Override
  public synchronized Generation allocate(String base) {
    int next = list(base).stream().mapToInt(Generation::number).max().orElse(0) + 1;
    try {
      Files.createDirectories(dir(base));
      while (Files.exists(pending(new Generation(base, next)))) {
        next++;
      }
      Generation g = new Generation(base, next);
      Files.createFile(pending(g));
      return g;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public OutputStream openOutput(Generation generation) throws IOException {
    return Files.newOutputStream(pending(generation));
  }

  @Override
  public synchronized void commit(Generation generation) {
    try {
      Files.move(pending(generation), file(generation));
      List<Generation> all = list(generation.base());
      for (int i = 0; i < all.size() - limit(generation.base()); i++) {
        Files.deleteIfExists(file(all.get(i)));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void discard(Generation generation) {
    try {
      Files.deleteIfExists(pending(generation));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public Optional<Generation> resolve(String base, int relative) {
    List<Generation> all = list(base);
    int idx = all.size() - 1 + relative;
    return idx >= 0 && idx < all.size() && relative <= 0
        ? Optional.of(all.get(idx))
        : Optional.empty();
  }

  @Override
  public InputStream openInput(Generation generation) throws IOException {
    return Files.newInputStream(file(generation));
  }

  @Override
  public List<Generation> list(String base) {
    if (!Files.isDirectory(dir(base))) {
      return List.of();
    }
    try (Stream<Path> s = Files.list(dir(base))) {
      return s.map(p -> GEN.matcher(p.getFileName().toString()))
          .filter(Matcher::matches)
          .map(m -> new Generation(base, Integer.parseInt(m.group(1))))
          .sorted(Comparator.comparingInt(Generation::number))
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public int limit(String base) {
    return limit;
  }
}
