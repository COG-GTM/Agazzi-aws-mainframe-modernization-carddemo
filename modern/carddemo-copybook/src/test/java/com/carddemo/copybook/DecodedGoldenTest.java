package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.copybook.codec.CodecOptions;
import com.carddemo.copybook.codec.VariableBlockFile;
import com.carddemo.copybook.generated.export.ExportRecord;
import com.carddemo.copybook.generated.readacct.ArrypsRecord;
import com.carddemo.copybook.generated.readacct.PscompRecord;
import com.carddemo.copybook.generated.readacct.VbpsRecord;
import com.carddemo.copybook.ims.ImsUnloadFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;

/**
 * Parity gate 1A: field-level decode of binary legacy datasets/goldens compared with the reviewed
 * fixtures in {@code golden/1a-copybook} (one {@code toString()} per record). Refresh with {@code
 * -Dgolden.update=true}.
 */
@Tag("parity")
class DecodedGoldenTest {

  record Case(String fixture, List<?> records) {}

  static Path fixtures() {
    return TestPaths.goldenRoot().resolve("1a-copybook");
  }

  static List<Case> cases() {
    Path readacct = TestPaths.goldenRoot().resolve("batch/READACCT/expected");
    List<Case> cases = new ArrayList<>();
    cases.add(
        new Case(
            "READACCT/PSCOMP.decoded.txt",
            ReadAcctGoldenTest.fixed(
                TestPaths.bytes(readacct.resolve("PSCOMP.bin")),
                PscompRecord.codec(CodecOptions.GNUCOBOL))));
    cases.add(
        new Case(
            "READACCT/ARRYPS.decoded.txt",
            ReadAcctGoldenTest.fixed(
                TestPaths.bytes(readacct.resolve("ARRYPS.bin")),
                ArrypsRecord.codec(CodecOptions.GNUCOBOL))));
    var vb = VbpsRecord.codec(CodecOptions.GNUCOBOL);
    cases.add(
        new Case(
            "READACCT/VBPS.decoded.txt",
            VariableBlockFile.read(
                    TestPaths.bytes(readacct.resolve("VBPS.bin")), VariableBlockFile.Rdw.GNUCOBOL)
                .stream()
                .map(vb::decode)
                .toList()));
    byte[] export =
        TestPaths.bytes(TestPaths.app().resolve("data/EBCDIC/AWS.M2.CARDDEMO.EXPORT.DATA.PS"));
    var ex = ExportRecord.codec(CodecOptions.EBCDIC);
    List<ExportRecord> exports = new ArrayList<>();
    for (int off = 0; off < export.length; off += 500) {
      exports.add(ex.decode(Arrays.copyOfRange(export, off, off + 500)));
    }
    cases.add(new Case("EXPORT/EXPORT.DATA.decoded.txt", exports));
    cases.add(
        new Case(
            "IMS/DBPAUTP0.decoded.txt",
            ImsUnloadFile.read(
                TestPaths.bytes(
                    TestPaths.app()
                        .resolve(
                            "app-authorization-ims-db2-mq/data/EBCDIC/"
                                + "AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat")))));
    return cases;
  }

  @TestFactory
  Stream<DynamicTest> decodedRecordsMatchFixtures() {
    boolean update = Boolean.getBoolean("golden.update");
    return cases().stream()
        .map(
            c ->
                DynamicTest.dynamicTest(
                    c.fixture(),
                    () -> {
                      StringBuilder sb = new StringBuilder();
                      c.records().forEach(r -> sb.append(r).append('\n'));
                      Path f = fixtures().resolve(c.fixture());
                      if (update) {
                        Files.createDirectories(f.getParent());
                        Files.writeString(f, sb.toString(), StandardCharsets.UTF_8);
                      }
                      assertThat(Files.readString(f, StandardCharsets.UTF_8))
                          .isEqualTo(sb.toString());
                    }));
  }
}
