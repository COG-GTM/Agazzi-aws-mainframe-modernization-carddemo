package com.carddemo.copybook;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.copybook.codec.CodecOptions;
import com.carddemo.copybook.generated.core.AccountRecord;
import com.carddemo.copybook.generated.core.CardRecord;
import com.carddemo.copybook.generated.core.CardXrefRecord;
import com.carddemo.copybook.generated.core.Cvcus01yCustomerRecord;
import com.carddemo.copybook.generated.core.DalytranRecord;
import com.carddemo.copybook.generated.core.DisGroupRecord;
import com.carddemo.copybook.generated.core.SecUserData;
import com.carddemo.copybook.generated.core.TranCatBalRecord;
import com.carddemo.copybook.generated.core.TranCatRecord;
import com.carddemo.copybook.generated.core.TranTypeRecord;
import com.carddemo.copybook.generated.export.AccountExport;
import com.carddemo.copybook.generated.export.CardExport;
import com.carddemo.copybook.generated.export.CardXrefExport;
import com.carddemo.copybook.generated.export.CustomerExport;
import com.carddemo.copybook.generated.export.ExportRecord;
import com.carddemo.copybook.generated.export.TransactionExport;
import com.carddemo.copybook.ims.ImsUnloadFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Byte-identical decode → encode of every record of {@code app/data/ASCII} and {@code EBCDIC}. */
class DatasetRoundTripTest {

  static final Map<String, Function<CodecOptions, RecordCodec<?>>> BY_DATASET =
      Map.ofEntries(
          Map.entry("ACCTDATA", AccountRecord::codec),
          Map.entry("ACCDATA", AccountRecord::codec),
          Map.entry("CARDDATA", CardRecord::codec),
          Map.entry("CARDXREF", CardXrefRecord::codec),
          Map.entry("CUSTDATA", Cvcus01yCustomerRecord::codec),
          Map.entry("DALYTRAN", DalytranRecord::codec),
          Map.entry("DAILYTRAN", DalytranRecord::codec),
          Map.entry("DISCGRP", DisGroupRecord::codec),
          Map.entry("TCATBAL", TranCatBalRecord::codec),
          Map.entry("TCATBALF", TranCatBalRecord::codec),
          Map.entry("TRANCATG", TranCatRecord::codec),
          Map.entry("TRANTYPE", TranTypeRecord::codec),
          Map.entry("USRSEC", SecUserData::codec));

  static Stream<Arguments> asciiFiles() throws Exception {
    try (Stream<Path> s = Files.list(TestPaths.app().resolve("data/ASCII"))) {
      return s
          .filter(p -> p.toString().endsWith(".txt"))
          .sorted()
          .map(Arguments::of)
          .toList()
          .stream();
    }
  }

  static Stream<Arguments> ebcdicFiles() throws Exception {
    try (Stream<Path> s = Files.list(TestPaths.app().resolve("data/EBCDIC"))) {
      return s
          .filter(p -> p.getFileName().toString().contains(".PS"))
          .filter(p -> !p.getFileName().toString().contains("EXPORT"))
          .filter(p -> !p.getFileName().toString().endsWith(".INIT"))
          .sorted()
          .map(Arguments::of)
          .toList()
          .stream();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("asciiFiles")
  void asciiDatasetsRoundTrip(Path file) throws Exception {
    String ds = file.getFileName().toString().replace(".txt", "").toUpperCase();
    RecordCodec<?> codec = BY_DATASET.get(ds).apply(CodecOptions.ASCII);
    List<String> lines =
        Files.readAllLines(file, StandardCharsets.ISO_8859_1).stream()
            .map(l -> l.replace("\r", ""))
            .filter(l -> !l.isBlank())
            .toList();
    assertThat(lines).isNotEmpty();
    for (String line : lines) {
      byte[] rec =
          (line + " ".repeat(codec.recordLength() - line.length()))
              .getBytes(StandardCharsets.ISO_8859_1);
      assertThat(roundTrip(codec, rec)).as("%s: %s", ds, line).isEqualTo(rec);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ebcdicFiles")
  void ebcdicDatasetsRoundTrip(Path file) {
    String ds = file.getFileName().toString().replace("AWS.M2.CARDDEMO.", "").split("\\.")[0];
    RecordCodec<?> codec = BY_DATASET.get(ds).apply(CodecOptions.EBCDIC);
    byte[] all = TestPaths.bytes(file);
    assertThat(all.length % codec.recordLength()).as(ds).isZero();
    for (int off = 0; off < all.length; off += codec.recordLength()) {
      byte[] rec = Arrays.copyOfRange(all, off, off + codec.recordLength());
      assertThat(roundTrip(codec, rec)).as("%s @%d", ds, off).isEqualTo(rec);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("ebcdicFiles")
  void ebcdicDecodesToSameValuesAsAscii(Path file) throws Exception {
    String ds = file.getFileName().toString().replace("AWS.M2.CARDDEMO.", "").split("\\.")[0];
    Path ascii = TestPaths.app().resolve("data/ASCII/" + asciiName(ds));
    if (!Files.exists(ascii) || file.getFileName().toString().endsWith(".INIT")) {
      return;
    }
    RecordCodec<?> e = BY_DATASET.get(ds).apply(CodecOptions.EBCDIC);
    RecordCodec<?> a = BY_DATASET.get(ds).apply(CodecOptions.ASCII);
    List<String> lines =
        Files.readAllLines(ascii, StandardCharsets.ISO_8859_1).stream()
            .filter(l -> !l.isBlank())
            .toList();
    byte[] all = TestPaths.bytes(file);
    assertThat(all.length / e.recordLength()).isEqualTo(lines.size());
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      Object fromAscii =
          a.decode(
              (line + " ".repeat(a.recordLength() - line.length()))
                  .getBytes(StandardCharsets.ISO_8859_1));
      Object fromEbcdic =
          e.decode(Arrays.copyOfRange(all, i * e.recordLength(), (i + 1) * e.recordLength()));
      // the EBCDIC and ASCII extracts differ in a few non-key fields (e.g. ACCTDATA #48 zip)
      assertThat(firstComponent(fromEbcdic))
          .as("%s #%d", ds, i)
          .isEqualTo(firstComponent(fromAscii));
    }
  }

  private static Object firstComponent(Object rec) throws Exception {
    return rec.getClass().getRecordComponents()[0].getAccessor().invoke(rec);
  }

  @Test
  void dailyTranInitIsALowValuesPlaceholder() {
    byte[] init =
        TestPaths.bytes(TestPaths.app().resolve("data/EBCDIC/AWS.M2.CARDDEMO.DALYTRAN.PS.INIT"));
    assertThat(init).hasSize(DalytranRecord.LAYOUT.length());
    // LOW-VALUES in PIC 9 fields: not a DALYTRAN record, the codec refuses it instead of guessing
    assertThat(Arrays.copyOf(init, 300)).containsOnly((byte) 0);
    org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
        .isThrownBy(() -> DalytranRecord.codec(CodecOptions.EBCDIC).decode(init));
  }

  private static String asciiName(String ds) {
    return switch (ds) {
      case "DALYTRAN" -> "dailytran.txt";
      case "TCATBALF" -> "tcatbal.txt";
      default -> ds.toLowerCase() + ".txt";
    };
  }

  @Test
  void exportDataRoundTripsThroughTheSealedFamily() {
    byte[] all =
        TestPaths.bytes(TestPaths.app().resolve("data/EBCDIC/AWS.M2.CARDDEMO.EXPORT.DATA.PS"));
    RecordCodec<ExportRecord> codec = ExportRecord.codec(CodecOptions.EBCDIC);
    assertThat(codec.recordLength()).isEqualTo(500);
    assertThat(all.length).isEqualTo(500 * 500);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Map<String, Long> byType = new TreeMap<>();
    for (int off = 0; off < all.length; off += 500) {
      ExportRecord r = codec.decode(Arrays.copyOfRange(all, off, off + 500));
      byType.merge(r.getClass().getSimpleName(), 1L, Long::sum);
      assertThat(r.exportSequenceNum()).isPositive();
      out.writeBytes(codec.encode(r));
    }
    assertThat(out.toByteArray()).isEqualTo(all);
    assertThat(byType)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                CustomerExport.class.getSimpleName(), 50L,
                AccountExport.class.getSimpleName(), 50L,
                CardXrefExport.class.getSimpleName(), 50L,
                CardExport.class.getSimpleName(), 50L,
                TransactionExport.class.getSimpleName(), 300L));
  }

  @Test
  void exportVariantsCarryTheSameDataAsTheSourceFiles() {
    byte[] all =
        TestPaths.bytes(TestPaths.app().resolve("data/EBCDIC/AWS.M2.CARDDEMO.EXPORT.DATA.PS"));
    RecordCodec<ExportRecord> codec = ExportRecord.codec(CodecOptions.EBCDIC);
    List<ExportRecord> recs =
        Stream.iterate(0, o -> o < all.length, o -> o + 500)
            .map(o -> codec.decode(Arrays.copyOfRange(all, o, o + 500)))
            .toList();
    Map<Long, AccountExport> accounts =
        recs.stream()
            .filter(AccountExport.class::isInstance)
            .map(AccountExport.class::cast)
            .collect(Collectors.toMap(a -> a.exportAccountData().expAcctId(), a -> a));
    byte[] acct =
        TestPaths.bytes(TestPaths.app().resolve("data/EBCDIC/AWS.M2.CARDDEMO.ACCTDATA.PS"));
    var acctCodec = AccountRecord.codec(CodecOptions.EBCDIC);
    for (int off = 0; off < acct.length; off += 300) {
      AccountRecord a = acctCodec.decode(Arrays.copyOfRange(acct, off, off + 300));
      AccountExport x = accounts.get(a.acctId());
      assertThat(x).as("account %d exported", a.acctId()).isNotNull();
      assertThat(x.exportAccountData().expAcctCashCreditLimit())
          .isEqualByComparingTo(a.acctCashCreditLimit());
      assertThat(x.exportAccountData().expAcctCreditLimit())
          .isEqualByComparingTo(a.acctCreditLimit());
    }
  }

  @Test
  void imsUnloadRoundTrips() {
    byte[] file =
        TestPaths.bytes(
            TestPaths.app()
                .resolve(
                    "app-authorization-ims-db2-mq/data/EBCDIC/"
                        + "AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat"));
    List<ImsUnloadFile.Entry> entries = ImsUnloadFile.read(file);
    assertThat(entries).hasSize(226);
    Map<String, Long> kinds =
        entries.stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.getClass().getSimpleName(), TreeMap::new, Collectors.counting()));
    // header + trailer + one blank PAUTSUM0 (PA-ACCT-ID = spaces) stay verbatim
    assertThat(kinds)
        .containsEntry("Control", 3L)
        .containsEntry("Summary", 21L)
        .containsEntry("Detail", 202L);
    assertThat(ImsUnloadFile.write(entries)).isEqualTo(file);
    ImsUnloadFile.Summary first =
        entries.stream()
            .filter(ImsUnloadFile.Summary.class::isInstance)
            .map(ImsUnloadFile.Summary.class::cast)
            .findFirst()
            .orElseThrow();
    assertThat(first.segment().paAcctId()).isPositive();
  }

  @SuppressWarnings("unchecked")
  static <T> byte[] roundTrip(RecordCodec<T> codec, byte[] rec) {
    T v = codec.decode(rec);
    return codec.encode(v);
  }
}
