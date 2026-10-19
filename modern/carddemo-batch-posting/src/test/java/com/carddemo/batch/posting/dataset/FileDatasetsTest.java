package com.carddemo.batch.posting.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.batch.posting.golden.Golden;
import com.carddemo.batch.posting.support.CobolAbendException;
import com.carddemo.contracts.record.AccountRecord;
import com.carddemo.copybook.CoreCodecs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDatasetsTest {

  @TempDir Path dir;

  @Test
  void lineSequentialPadsCrlfLinesAndRejectsTooLongRecords() throws Exception {
    List<String> recs =
        LineSequentialFile.read(
            new ByteArrayInputStream("ab\r\n\ncd\n".getBytes(StandardCharsets.US_ASCII)), 4, "X");
    assertThat(recs).containsExactly("ab  ", "cd  ");
    assertThatThrownBy(
            () ->
                LineSequentialFile.read(
                    new ByteArrayInputStream("abcde\n".getBytes(StandardCharsets.US_ASCII)),
                    4,
                    "X"))
        .isInstanceOf(CobolAbendException.class)
        .hasMessageContaining("MALFORMED RECORD 1 IN X");
    assertThatThrownBy(() -> LineSequentialFile.read(dir.resolve("none.dat"), 4))
        .isInstanceOf(CobolAbendException.class)
        .satisfies(e -> assertThat(((CobolAbendException) e).fileStatus()).isEqualTo("35"));
  }

  @Test
  void ksdsReadWriteRewriteFlushInKeyOrder() {
    Golden.copy(Golden.seed("acctdata.txt"), dir, FileDatasets.ACCTDATA);
    FileDatasets ds = new FileDatasets(dir);
    KeyedDataset<AccountRecord> acct = ds.accounts();
    List<AccountRecord> all = acct.records();
    assertThat(all).hasSize(50);
    AccountRecord first = all.get(0);
    assertThat(acct.read(Keys.account(first))).contains(first);
    assertThat(acct.read(Keys.account(999_999L))).isEmpty();
    assertThatThrownBy(() -> acct.write(first)).isInstanceOf(DuplicateKeyException.class);
    AccountRecord copy =
        new AccountRecord(
            99_999_999_999L,
            first.acctActiveStatus(),
            first.acctCurrBal(),
            first.acctCreditLimit(),
            first.acctCashCreditLimit(),
            first.acctOpenDate(),
            first.acctExpirationDate(),
            first.acctReissueDate(),
            first.acctCurrCycCredit(),
            first.acctCurrCycDebit(),
            first.acctAddrZip(),
            first.acctGroupId(),
            first.filler());
    assertThatThrownBy(() -> acct.rewrite(copy)).isInstanceOf(RecordNotFoundException.class);
    acct.write(copy);
    ds.flush();
    List<String> lines = LineSequentialFile.read(ds.file(FileDatasets.ACCTDATA), 300);
    assertThat(lines).hasSize(51);
    assertThat(CoreCodecs.ACCOUNT.decodeLine(lines.get(50))).isEqualTo(copy);
  }

  @Test
  void missingMandatoryClusterAbendsWithStatus35AndDuplicatesAbendOnLoad() throws Exception {
    FileDatasets ds = new FileDatasets(dir);
    assertThatThrownBy(() -> ds.cards().open())
        .isInstanceOf(CobolAbendException.class)
        .hasMessage("ERROR OPENING CARDDATA");
    assertThat(ds.transactions().records()).isEmpty();
    String line = Files.readAllLines(Golden.seed("cardxref.txt")).get(0);
    Files.write(dir.resolve("CARDXREF.dat"), List.of(line, line));
    assertThatThrownBy(() -> new FileDatasets(dir).cardXref().open())
        .isInstanceOf(CobolAbendException.class)
        .hasMessageContaining("DUPLICATE KEY");
  }

  @Test
  void malformedNumericFieldAbends() throws Exception {
    String line = Files.readAllLines(Golden.seed("acctdata.txt")).get(0);
    Files.write(dir.resolve("ACCTDATA.dat"), List.of("X" + line.substring(1)));
    assertThatThrownBy(() -> new FileDatasets(dir).accounts().open())
        .isInstanceOf(CobolAbendException.class)
        .hasMessageContaining("MALFORMED RECORD 1 IN ACCTDATA");
  }

  @Test
  void failedAixUpgradePublishesNoMasterFile() throws Exception {
    Golden.copy(Golden.seed("acctdata.txt"), dir, FileDatasets.ACCTDATA);
    Files.writeString(dir.resolve("TRANSACT.dat"), "");
    Files.writeString(dir.resolve(FileDatasets.TRANSACT_AIX_FILE), "");
    Files.createDirectory(dir.resolve(FileDatasets.TRANSACT_AIX_FILE + ".tmp"));
    byte[] accounts = Files.readAllBytes(dir.resolve("ACCTDATA.dat"));
    FileDatasets ds = new FileDatasets(dir);
    ds.transactions()
        .write(
            CoreCodecs.TRANSACTION.decodeLine(
                Golden.expected(Golden.batch("COMBTRAN"), "TRANSACT", 350).get(0)));
    ds.accounts().clear();
    assertThatThrownBy(ds::flush).isInstanceOf(java.io.UncheckedIOException.class);
    assertThat(Files.readString(dir.resolve("TRANSACT.dat"))).isEmpty();
    assertThat(Files.readAllBytes(dir.resolve("ACCTDATA.dat"))).isEqualTo(accounts);
    assertThat(Files.exists(dir.resolve("TRANSACT.dat.tmp"))).isFalse();
    assertThat(Files.exists(dir.resolve("ACCTDATA.dat.tmp"))).isFalse();
  }

  @Test
  void clearEmptiesAndMarksDirty() {
    Golden.copy(Golden.seed("acctdata.txt"), dir, FileDatasets.ACCTDATA);
    InMemoryKeyedDataset<AccountRecord> ds =
        new InMemoryKeyedDataset<>(
            "ACCTDATA", dir.resolve("ACCTDATA.dat"), CoreCodecs.ACCOUNT, Keys::account, true);
    ds.clear();
    assertThat(ds.isDirty()).isTrue();
    assertThat(ds.name()).isEqualTo("ACCTDATA");
    ds.flush();
    assertThat(ds.isDirty()).isFalse();
    assertThat(LineSequentialFile.read(dir.resolve("ACCTDATA.dat"), 300)).isEmpty();
  }
}
