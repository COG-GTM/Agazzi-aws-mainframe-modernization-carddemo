package com.carddemo.batch.posting.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.posting.cbtrn02c.Cbtrn02cPoster;
import com.carddemo.batch.posting.cbtrn02c.PostingResult;
import com.carddemo.batch.posting.dataset.FileDatasets;
import com.carddemo.batch.posting.dataset.LineSequentialFile;
import com.carddemo.batch.posting.support.Sysout;
import com.carddemo.copybook.CoreCodecs;
import com.carddemo.platform.time.FixedBusinessClock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** POSTTRAN / CBTRN02C file-in/file-out parity with the GnuCOBOL golden masters. */
@Tag("parity")
class PosttranGoldenParityTest {

  @TempDir Path tmp;

  static Stream<Arguments> goldens() {
    Path edge = Golden.b1("POSTTRAN-EDGE").resolve("input");
    return Stream.of(
        Arguments.of(
            "POSTTRAN",
            Golden.b1("POSTTRAN"),
            Golden.seed("dailytran.txt"),
            Golden.seed("cardxref.txt"),
            Golden.seed("acctdata.txt"),
            Golden.seed("tcatbal.txt")),
        Arguments.of(
            "POSTTRAN-EDGE",
            Golden.b1("POSTTRAN-EDGE"),
            edge.resolve("dailytran.txt"),
            edge.resolve("cardxref.txt"),
            edge.resolve("acctdata.txt"),
            edge.resolve("tcatbal.txt")),
        Arguments.of(
            "POSTTRAN-EMPTY",
            Golden.b1("POSTTRAN-EMPTY"),
            null,
            Golden.seed("cardxref.txt"),
            Golden.seed("acctdata.txt"),
            Golden.seed("tcatbal.txt")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldens")
  void cbtrn02cMatchesGoldenMaster(
      String name, Path golden, Path dalytran, Path xref, Path acct, Path tcatbal)
      throws Exception {
    if (dalytran == null) {
      Files.createDirectories(tmp);
      Files.writeString(tmp.resolve("DALYTRAN.dat"), "");
    } else {
      Golden.copy(dalytran, tmp, FileDatasets.DALYTRAN);
    }
    Golden.copy(xref, tmp, FileDatasets.CARDXREF);
    Golden.copy(acct, tmp, FileDatasets.ACCTDATA);
    Golden.copy(tcatbal, tmp, FileDatasets.TCATBALF);

    FileDatasets ds = new FileDatasets(tmp);
    List<String> rejects = new ArrayList<>();
    Sysout sysout = new Sysout(Cbtrn02cPoster.PROGRAM);
    PostingResult result =
        new Cbtrn02cPoster(FixedBusinessClock.golden()).run(ds, rejects::add, sysout);
    ds.flush();

    assertThat(List.of("STEP15 " + result.returnCode())).isEqualTo(Golden.returnCodes(golden));
    assertThat(Golden.rtrim(sysout.lines())).isEqualTo(Golden.sysout(golden));
    Golden.assertSameRejects(Golden.expected(golden, "DALYREJS", 430), rejects);
    Golden.assertSameRecords(
        "TRANSACT",
        Golden.expected(golden, "TRANSACT", 350),
        read(tmp, "TRANSACT", 350),
        CoreCodecs.TRANSACTION);
    Golden.assertSameRecords(
        "ACCTDATA",
        Golden.expected(golden, "ACCTDATA", 300),
        read(tmp, "ACCTDATA", 300),
        CoreCodecs.ACCOUNT);
    Golden.assertSameRecords(
        "TCATBALF",
        Golden.expected(golden, "TCATBALF", 50),
        read(tmp, "TCATBALF", 50),
        CoreCodecs.TRAN_CAT_BALANCE);
  }

  private static List<String> read(Path dir, String ds, int lrecl) {
    Path p = dir.resolve(ds + ".dat");
    return Files.exists(p) ? LineSequentialFile.read(p, lrecl) : List.of();
  }
}
