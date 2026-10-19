package com.carddemo.perf.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.contracts.port.RecordCodec;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.contracts.record.CardRecord;
import com.carddemo.contracts.record.CardXrefRecord;
import com.carddemo.contracts.record.CustomerRecord;
import com.carddemo.contracts.record.DailyTransactionRecord;
import com.carddemo.contracts.record.TranCatBalanceRecord;
import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SyntheticDataGeneratorTest {

  static final int SCALE = 1_000;
  static final long SEED = DataGenMain.DEFAULT_SEED;

  @TempDir static Path out;
  static SampleData sample;
  static SyntheticDataGenerator.Stats stats;

  static Path appData() {
    return Path.of(System.getProperty("modern.root", "../.."))
        .resolve("../app/data/ASCII")
        .normalize();
  }

  @BeforeAll
  static void generateTimesThousand() {
    sample = SampleData.load(appData());
    stats = new SyntheticDataGenerator(sample, SCALE, SEED).generate(out);
  }

  static Stream<Arguments> generatedFiles() {
    return Stream.of(
        Arguments.of("acctdata.txt", CoreCodecs.ACCOUNT, 300, 50),
        Arguments.of("carddata.txt", CoreCodecs.CARD, 150, 50),
        Arguments.of("cardxref.txt", CoreCodecs.CARD_XREF, 50, 50),
        Arguments.of("custdata.txt", CoreCodecs.CUSTOMER, 500, 50),
        Arguments.of("tcatbal.txt", CoreCodecs.TRAN_CAT_BALANCE, 50, 50),
        Arguments.of("dailytran.txt", CoreCodecs.DAILY_TRANSACTION, 350, 300),
        Arguments.of("transact.txt", CoreCodecs.TRANSACTION, 350, 300));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generatedFiles")
  void filesAreThousandTimesTheSampleAtPlanLrecl(
      String file, RecordCodec<?> codec, int lrecl, int samplePerCopy) throws IOException {
    List<String> lines = lines(file);
    assertThat(codec.recordLength()).as("plan §1.3 LRECL").isEqualTo(lrecl);
    assertThat(lines).hasSize(samplePerCopy * SCALE);
    assertThat(stats.counts()).containsEntry(file, (long) samplePerCopy * SCALE);
    assertThat(lines.stream().filter(l -> l.length() != lrecl)).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generatedFiles")
  void roundTripsThroughFase0Codecs(String file, RecordCodec<?> codec, int lrecl, int samplePerCopy)
      throws IOException {
    assertThat(lines(file).stream().filter(l -> !roundTrip(codec, l).equals(l))).isEmpty();
  }

  @Test
  void copyZeroIsTheSampleByteForByte() throws IOException {
    for (String f : List.of("acctdata.txt", "carddata.txt", "custdata.txt", "dailytran.txt")) {
      RecordCodec<?> codec = codecFor(f);
      List<String> expected =
          SampleData.lines(appData(), f).stream()
              .map(l -> roundTrip(codec, SampleData.fit(l, codec)))
              .toList();
      assertThat(lines(f).subList(0, expected.size())).as(f).isEqualTo(expected);
    }
  }

  @Test
  void referenceFilesAreCopiedAtFullLrecl() throws IOException {
    assertThat(lines("trantype.txt")).hasSize(7);
    assertThat(lines("trancatg.txt")).hasSize(18);
    assertThat(lines("discgrp.txt")).hasSize(51);
    assertThat(Files.list(out.resolve("datasets")).count())
        .isEqualTo(SyntheticDataGenerator.DATASETS.size());
    assertThat(Files.mismatch(out.resolve("datasets/ACCTDATA.dat"), ascii("acctdata.txt")))
        .isEqualTo(-1L);
  }

  @Test
  void keysAreUniqueAndForeignKeysResolve() throws IOException {
    List<AccountRecord> accts = decode("acctdata.txt", CoreCodecs.ACCOUNT);
    List<CardRecord> cards = decode("carddata.txt", CoreCodecs.CARD);
    List<CardXrefRecord> xrefs = decode("cardxref.txt", CoreCodecs.CARD_XREF);
    List<CustomerRecord> custs = decode("custdata.txt", CoreCodecs.CUSTOMER);
    List<TranCatBalanceRecord> tcat = decode("tcatbal.txt", CoreCodecs.TRAN_CAT_BALANCE);
    List<DailyTransactionRecord> daily = decode("dailytran.txt", CoreCodecs.DAILY_TRANSACTION);
    List<TransactionRecord> posted = decode("transact.txt", CoreCodecs.TRANSACTION);

    Set<Long> acctIds = unique(accts.stream().map(AccountRecord::acctId).toList());
    Set<String> cardNums = unique(cards.stream().map(CardRecord::cardNum).toList());
    Set<Integer> custIds = unique(custs.stream().map(CustomerRecord::custId).toList());
    Set<String> xrefCards = unique(xrefs.stream().map(CardXrefRecord::xrefCardNum).toList());
    unique(daily.stream().map(DailyTransactionRecord::dalytranId).toList());
    unique(posted.stream().map(TransactionRecord::tranId).toList());
    unique(
        tcat.stream()
            .map(t -> t.trancatAcctId() + "/" + t.trancatTypeCd() + "/" + t.trancatCd())
            .toList());

    assertThat(cards.stream().filter(c -> !acctIds.contains(c.cardAcctId())))
        .as("card -> account")
        .isEmpty();
    assertThat(
            xrefs.stream()
                .filter(
                    x ->
                        !cardNums.contains(x.xrefCardNum())
                            || !custIds.contains(x.xrefCustId())
                            || !acctIds.contains(x.xrefAcctId())))
        .as("xref -> card/customer/account")
        .isEmpty();
    assertThat(tcat.stream().filter(t -> !acctIds.contains(t.trancatAcctId())))
        .as("tcatbal -> account")
        .isEmpty();
    assertThat(posted.stream().filter(t -> !xrefCards.contains(t.tranCardNum())))
        .as("transact -> xref")
        .isEmpty();
    assertThat(daily.stream().filter(d -> !xrefCards.contains(d.dalytranCardNum())))
        .as("dalytran -> xref")
        .isEmpty();
    assertThat(cardNums.stream().filter(c -> !c.matches("[0-9]{16}"))).isEmpty();
  }

  @Test
  void signedAmountsUseOverpunch() throws IOException {
    List<DailyTransactionRecord> daily = decode("dailytran.txt", CoreCodecs.DAILY_TRANSACTION);
    assertThat(daily.stream().anyMatch(d -> d.dalytranAmt().signum() < 0)).isTrue();
    Set<Character> signs =
        lines("dailytran.txt").stream().map(l -> l.charAt(132 + 10)).collect(Collectors.toSet());
    assertThat(signs).containsAnyOf('}', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R');
    assertThat(signs).containsAnyOf('{', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I');
  }

  @Test
  void sameSeedSameBytesOtherSeedOtherBytes(@TempDir Path a, @TempDir Path b, @TempDir Path c)
      throws IOException {
    new SyntheticDataGenerator(sample, 3, 42).generate(a);
    new SyntheticDataGenerator(sample, 3, 42).generate(b);
    new SyntheticDataGenerator(sample, 3, 43).generate(c);
    for (String f :
        List.of("ascii/acctdata.txt", "ascii/dailytran.txt", "csv/account.csv", "load.sql")) {
      assertThat(Files.mismatch(a.resolve(f), b.resolve(f))).as(f).isEqualTo(-1L);
    }
    assertThat(Files.mismatch(a.resolve("ascii/acctdata.txt"), c.resolve("ascii/acctdata.txt")))
        .isNotEqualTo(-1L);
  }

  @Test
  void csvAndLoadScriptMatchSchemaV1() throws IOException {
    List<String> csv = Files.readAllLines(out.resolve("csv/account.csv"));
    assertThat(csv).hasSize(50 * SCALE + 1);
    assertThat(csv.get(0)).isEqualTo(SyntheticDataGenerator.ACCOUNT_COLS.replace(" ", ""));
    assertThat(csv.get(1)).startsWith("1,\"Y\",");
    assertThat(Files.readAllLines(out.resolve("csv/daily_transaction.csv")))
        .hasSize(300 * SCALE + 1);
    String sql = Files.readString(out.resolve("load.sql"));
    for (String t : SyntheticDataGenerator.LOAD_ORDER) {
      assertThat(sql).contains("\\copy " + t + " (").contains("'csv/" + t + ".csv'");
      assertThat(out.resolve("csv/" + t + ".csv")).exists();
    }
    assertThat(Files.readString(out.resolve("k6/fixtures.json")))
        .startsWith("{\"seed\":" + SEED + ",\"scale\":" + SCALE + ",\"accounts\":[{");
  }

  @Test
  void rejectsScaleOutOfRange() {
    assertThatThrownBy(() -> new SyntheticDataGenerator(sample, 0, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SyntheticDataGenerator(sample, KeyMapper.MAX_SCALE + 1, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static <T> String roundTrip(RecordCodec<T> codec, String line) {
    return codec.encodeLine(codec.decodeLine(line));
  }

  private static RecordCodec<?> codecFor(String file) {
    return generatedFiles()
        .filter(a -> a.get()[0].equals(file))
        .map(a -> (RecordCodec<?>) a.get()[1])
        .findFirst()
        .orElseThrow();
  }

  private static <T> Set<T> unique(List<T> keys) {
    Set<T> s = new HashSet<>(keys);
    assertThat(s).as("duplicate keys").hasSize(keys.size());
    return s;
  }

  @Test
  void transactHistoryDoesNotDuplicatePendingDailyTransactions() throws IOException {
    Set<String> pending =
        decode("dailytran.txt", CoreCodecs.DAILY_TRANSACTION).stream()
            .map(d -> d.dalytranCardNum() + d.dalytranOrigTs())
            .collect(Collectors.toSet());
    assertThat(
            decode("transact.txt", CoreCodecs.TRANSACTION).stream()
                .filter(t -> pending.contains(t.tranCardNum() + t.tranOrigTs()))
                .count())
        .isZero();
  }

  private static <T> List<T> decode(String file, RecordCodec<T> codec) throws IOException {
    return lines(file).stream().map(codec::decodeLine).toList();
  }

  private static Path ascii(String file) {
    return out.resolve("ascii").resolve(file);
  }

  private static List<String> lines(String file) throws IOException {
    return Files.readAllLines(ascii(file), StandardCharsets.US_ASCII);
  }
}
