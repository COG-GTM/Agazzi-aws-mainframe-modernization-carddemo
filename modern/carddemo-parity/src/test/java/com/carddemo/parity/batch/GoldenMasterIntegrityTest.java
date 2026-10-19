package com.carddemo.parity.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.parity.ParityPaths;
import com.carddemo.parity.compare.ParityExceptions;
import com.carddemo.parity.compare.RecordComparator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Parity gate, Fase 0 part: every captured golden master is complete, its records decode with the
 * contracts-v1 layout (byte-identical round trip) and the field comparator sees zero diffs on it.
 * B1-B5 add the Java-vs-golden tests for their jobs next to this one.
 */
@Tag("parity")
class GoldenMasterIntegrityTest {

  @TestFactory
  Stream<DynamicTest> goldenMastersAreCompleteAndDecodable() throws Exception {
    Path root = ParityPaths.modernRoot();
    RecordComparator cmp =
        new RecordComparator(ParityExceptions.load(root.resolve("PARITY_EXCEPTIONS.md")));
    return GoldenJob.jobNames(root).stream()
        .map(
            name ->
                DynamicTest.dynamicTest(
                    name,
                    () -> {
                      GoldenJob job = GoldenJob.load(root, name);
                      assertThat(job.outputs()).isNotEmpty();
                      assertThat(job.returnCodes())
                          .isNotEmpty()
                          .allSatisfy((s, rc) -> assertThat(rc).isLessThanOrEqualTo(4));
                      for (String out : job.outputs()) {
                        GoldenJob.Dataset ds = job.datasets().get(out);
                        Path file = job.expectedOutput(out);
                        assertThat(file).as("%s/%s", name, out).exists();
                        if (ds == null
                            || ds.layout() == null
                            || !file.toString().endsWith(".dat")) {
                          continue;
                        }
                        var recs = RecordComparator.readRecords(file, ds.lrecl());
                        assertThat(cmp.compare(name, out, ds.layout(), recs, recs)).isEmpty();
                        if (CoreCodecs.all().containsKey(ds.layout())) {
                          var codec = CoreCodecs.forCopybook(ds.layout());
                          var reencoded = recs.stream().map(r -> roundTrip(codec, r)).toList();
                          assertThat(cmp.compare(name, out, ds.layout(), recs, reencoded))
                              .isEmpty();
                        }
                      }
                    }));
  }

  /** decode -> encode -> decode is value-stable (sign may be normalised to overpunch). */
  private static <T> String roundTrip(RecordCodec<T> codec, String r) {
    T value = codec.decode(r.getBytes(StandardCharsets.ISO_8859_1));
    byte[] again = codec.encode(value);
    assertThat(codec.decode(again)).isEqualTo(value);
    return new String(again, StandardCharsets.ISO_8859_1);
  }

  @TestFactory
  Stream<DynamicTest> requiredGoldensPresent() {
    Path root = GoldenJob.root(ParityPaths.modernRoot());
    return Stream.of("POSTTRAN", "INTCALC")
        .map(
            j ->
                DynamicTest.dynamicTest(
                    j, () -> assertThat(Files.isDirectory(root.resolve(j))).isTrue()));
  }
}
