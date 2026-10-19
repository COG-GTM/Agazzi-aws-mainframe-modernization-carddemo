package com.carddemo.batch.posting.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.batch.posting.cbtrn01c.Cbtrn01cVerifier;
import com.carddemo.batch.posting.dataset.FileDatasets;
import com.carddemo.batch.posting.idcams.TransactionUtilities;
import com.carddemo.batch.posting.support.Sysout;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** COMBTRAN STEP05R and CBTRN01C parity with their golden masters (program level). */
@Tag("parity")
class BatchUtilitiesGoldenParityTest {

  @TempDir Path tmp;

  @Test
  void combtranSortIsByteIdenticalToGolden() {
    Path combtran = Golden.batch("COMBTRAN");
    List<String> sorted =
        TransactionUtilities.sortByTranId(
            List.of(
                Golden.expected(Golden.batch("POSTTRAN"), "TRANSACT", 350),
                Golden.expected(Golden.batch("INTCALC"), "SYSTRAN", 350)));
    assertThat(sorted).isEqualTo(Golden.expected(combtran, "COMBINED", 350));
    assertThat(Golden.returnCodes(combtran)).containsExactly("STEP05R 0", "STEP10 0");
  }

  @Test
  void cbtrn01cSysoutMatchesGolden() {
    Path edge = Golden.b1("POSTTRAN-EDGE").resolve("input");
    Golden.copy(edge.resolve("dailytran.txt"), tmp, FileDatasets.DALYTRAN);
    Golden.copy(edge.resolve("cardxref.txt"), tmp, FileDatasets.CARDXREF);
    Golden.copy(edge.resolve("acctdata.txt"), tmp, FileDatasets.ACCTDATA);
    Golden.copy(Golden.seed("custdata.txt"), tmp, FileDatasets.CUSTDATA);
    Golden.copy(Golden.seed("carddata.txt"), tmp, FileDatasets.CARDDATA);
    Sysout sysout = new Sysout(Cbtrn01cVerifier.PROGRAM);
    int rc = new Cbtrn01cVerifier().run(new FileDatasets(tmp), sysout);
    Path golden = Golden.b1("CBTRN01C");
    assertThat(List.of("CBTRN01C " + rc)).isEqualTo(Golden.returnCodes(golden));
    assertThat(Golden.rtrim(sysout.lines())).isEqualTo(Golden.sysout(golden));
  }

  @Test
  void cbtrn01cOnEmptyInputMatchesGolden() throws Exception {
    Path edge = Golden.b1("POSTTRAN-EDGE").resolve("input");
    java.nio.file.Files.writeString(tmp.resolve("DALYTRAN.dat"), "");
    Golden.copy(edge.resolve("cardxref.txt"), tmp, FileDatasets.CARDXREF);
    Golden.copy(edge.resolve("acctdata.txt"), tmp, FileDatasets.ACCTDATA);
    Golden.copy(Golden.seed("custdata.txt"), tmp, FileDatasets.CUSTDATA);
    Golden.copy(Golden.seed("carddata.txt"), tmp, FileDatasets.CARDDATA);
    Sysout sysout = new Sysout(Cbtrn01cVerifier.PROGRAM);
    int rc = new Cbtrn01cVerifier().run(new FileDatasets(tmp), sysout);
    Path golden = Golden.b1("CBTRN01C-EMPTY");
    assertThat(List.of("CBTRN01C " + rc)).isEqualTo(Golden.returnCodes(golden));
    assertThat(Golden.rtrim(sysout.lines())).isEqualTo(Golden.sysout(golden));
  }
}
