package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.copybook.codec.CodecOptions;
import com.carddemo.copybook.codec.LayoutCodec;
import com.carddemo.copybook.codec.VariableBlockFile;
import com.carddemo.copybook.codec.VariantCodec;
import com.carddemo.copybook.generated.readacct.ArrypsRecord;
import com.carddemo.copybook.generated.readacct.PscompRecord;
import com.carddemo.copybook.generated.readacct.VbpsRecord;
import com.carddemo.copybook.generated.readacct.VbrcRec1;
import com.carddemo.copybook.generated.readacct.VbrcRec2;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** CBACT01C (READACCT) outputs captured by the GnuCOBOL golden harness: PSCOMP, ARRYPS, VBPS. */
class ReadAcctGoldenTest {

  static Path expected() {
    return TestPaths.goldenRoot().resolve("batch/READACCT/expected");
  }

  static List<AccountRecord> accounts() throws Exception {
    return Files.readAllLines(TestPaths.app().resolve("data/ASCII/acctdata.txt")).stream()
        .filter(l -> !l.isBlank())
        .map(CoreCodecs.ACCOUNT::decodeLine)
        .toList();
  }

  static <T> List<T> fixed(byte[] all, LayoutCodec<T> codec) {
    assertThat(all.length % codec.recordLength()).isZero();
    List<T> out = new ArrayList<>();
    for (int off = 0; off < all.length; off += codec.recordLength()) {
      out.add(codec.decode(Arrays.copyOfRange(all, off, off + codec.recordLength())));
    }
    return out;
  }

  static <T> byte[] encodeAll(List<T> recs, LayoutCodec<T> codec) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    recs.forEach(r -> out.writeBytes(codec.encode(r)));
    return out.toByteArray();
  }

  @Test
  void pscompRoundTripsAndMatchesAccounts() throws Exception {
    byte[] all = TestPaths.bytes(expected().resolve("PSCOMP.bin"));
    LayoutCodec<PscompRecord> codec = PscompRecord.codec(CodecOptions.GNUCOBOL);
    assertThat(codec.recordLength()).isEqualTo(107);
    List<PscompRecord> recs = fixed(all, codec);
    assertThat(encodeAll(recs, codec)).isEqualTo(all);
    List<AccountRecord> accts = accounts();
    assertThat(recs).hasSameSizeAs(accts);
    for (int i = 0; i < recs.size(); i++) {
      PscompRecord p = recs.get(i);
      AccountRecord a = accts.get(i);
      assertThat(p.outAcctId()).isEqualTo(a.acctId());
      assertThat(p.outAcctCurrBal()).isEqualByComparingTo(a.acctCurrBal());
      assertThat(p.outAcctCreditLimit()).isEqualByComparingTo(a.acctCreditLimit());
      assertThat(p.outAcctCurrCycCredit()).isEqualByComparingTo(a.acctCurrCycCredit());
      assertThat(p.outAcctGroupId()).isEqualTo(a.acctGroupId());
    }
  }

  @Test
  void arrypsRoundTrips() throws Exception {
    byte[] all = TestPaths.bytes(expected().resolve("ARRYPS.bin"));
    LayoutCodec<ArrypsRecord> codec = ArrypsRecord.codec(CodecOptions.GNUCOBOL);
    List<ArrypsRecord> recs = fixed(all, codec);
    assertThat(encodeAll(recs, codec)).isEqualTo(all);
    List<AccountRecord> accts = accounts();
    for (int i = 0; i < recs.size(); i++) {
      ArrypsRecord r = recs.get(i);
      assertThat(r.arrAcctId()).isEqualTo(accts.get(i).acctId());
      assertThat(r.arrAcctBal()).hasSize(5);
    }
  }

  @Test
  void vbpsRoundTripsWithRdw() throws Exception {
    byte[] all = TestPaths.bytes(expected().resolve("VBPS.bin"));
    List<byte[]> raw = VariableBlockFile.read(all, VariableBlockFile.Rdw.GNUCOBOL);
    VariantCodec<VbpsRecord> codec = VbpsRecord.codec(CodecOptions.GNUCOBOL);
    List<VbpsRecord> recs = raw.stream().map(codec::decode).toList();
    assertThat(recs.stream().filter(VbrcRec1.class::isInstance)).hasSize(50);
    assertThat(recs.stream().filter(VbrcRec2.class::isInstance)).hasSize(50);
    List<byte[]> again = recs.stream().map(codec::encode).toList();
    assertThat(VariableBlockFile.write(again, VariableBlockFile.Rdw.GNUCOBOL)).isEqualTo(all);
    VbrcRec1 first = (VbrcRec1) recs.get(0);
    AccountRecord a = accounts().get(0);
    assertThat(first.vb1AcctId()).isEqualTo(a.acctId());
    assertThat(first.vb1AcctActiveStatus()).isEqualTo(a.acctActiveStatus());
    VbrcRec2 second = (VbrcRec2) recs.get(1);
    assertThat(second.vb2AcctCurrBal()).isEqualByComparingTo(a.acctCurrBal());
    assertThat(new String(raw.get(1), 35, 4, StandardCharsets.ISO_8859_1))
        .isEqualTo(a.acctReissueDate().substring(0, 4));
  }
}
