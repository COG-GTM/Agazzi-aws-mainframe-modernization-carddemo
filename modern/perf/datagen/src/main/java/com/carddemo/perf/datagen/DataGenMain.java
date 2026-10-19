package com.carddemo.perf.datagen;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * CLI: {@code java -jar carddemo-perf-datagen.jar --sample ../app/data/ASCII --out target/perf-data
 * --scale 1000 --seed 20221231}.
 */
public final class DataGenMain {

  static final long DEFAULT_SEED = 20_221_231L;

  private DataGenMain() {}

  public static void main(String[] args) {
    Map<String, String> opts = new HashMap<>();
    for (int i = 0; i + 1 < args.length; i += 2) {
      opts.put(args[i].replaceFirst("^--", ""), args[i + 1]);
    }
    Path sample = Path.of(opts.getOrDefault("sample", "../app/data/ASCII"));
    Path out = Path.of(opts.getOrDefault("out", "target/perf-data"));
    int scale = Integer.parseInt(opts.getOrDefault("scale", "1000"));
    long seed = Long.parseLong(opts.getOrDefault("seed", Long.toString(DEFAULT_SEED)));
    long t0 = System.nanoTime();
    var stats = new SyntheticDataGenerator(SampleData.load(sample), scale, seed).generate(out);
    long ms = (System.nanoTime() - t0) / 1_000_000;
    System.out.printf("datagen seed=%d scale=%d out=%s elapsed_ms=%d%n", seed, scale, out, ms);
    stats.counts().forEach((f, n) -> System.out.printf("  %-14s %,12d records%n", f, n));
  }
}
