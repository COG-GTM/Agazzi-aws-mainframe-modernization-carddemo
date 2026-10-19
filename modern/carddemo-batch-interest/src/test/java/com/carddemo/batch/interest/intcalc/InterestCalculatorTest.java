package com.carddemo.batch.interest.intcalc;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.contracts.record.TransactionRecord;
import com.carddemo.copybook.CoreCodecs;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterestCalculatorTest {

  @ParameterizedTest(name = "{0} * {1} / 1200 = {2}")
  @CsvSource({
    "1234.56, 15.00, 15.43",
    "0.07, 25.00, 0.00",
    "100.00, 15.00, 1.25",
    "999999.99, 25.00, 20833.33",
    "-100.01, 15.00, -1.25",
    "-0.07, 25.00, 0.00",
    "500.00, 0.00, 0.00",
    "1194.00, 15.00, 14.92"
  })
  void monthlyInterestTruncatesLikeCompute(String bal, String rate, String expected) {
    BigDecimal r = InterestCalculator.monthlyInterest(new BigDecimal(bal), new BigDecimal(rate));
    assertThat(r).isEqualByComparingTo(expected);
    assertThat(r.scale()).isEqualTo(2);
  }

  @Test
  void neverRoundsHalfUp() {
    // 7.99 * 15 / 1200 = 0.099875 -> ROUNDED would give 0.10
    assertThat(InterestCalculator.monthlyInterest(new BigDecimal("7.99"), new BigDecimal("15.00")))
        .isEqualByComparingTo("0.09");
  }

  @Test
  void additionsTruncateHighOrderDigitsOnOverflow() {
    assertThat(
            InterestCalculator.newBalance(new BigDecimal("9999999999.99"), new BigDecimal("0.01")))
        .isEqualByComparingTo("0.00");
    assertThat(
            InterestCalculator.addToTotal(new BigDecimal("999999999.99"), new BigDecimal("0.02")))
        .isEqualByComparingTo("0.01");
    assertThat(InterestCalculator.newBalance(new BigDecimal("194.00"), new BigDecimal("16.68")))
        .isEqualByComparingTo("210.68");
  }

  @Test
  void tranIdIsParmDatePlusSixDigitSuffix() {
    assertThat(InterestCalculator.tranId("2022071800", 1)).isEqualTo("2022071800000001");
    assertThat(InterestCalculator.tranId("2022071800", 123456)).isEqualTo("2022071800123456");
    assertThat(InterestCalculator.tranId("2022", 7)).isEqualTo("2022      000007");
    assertThat(InterestCalculator.tranId(null, 7)).isEqualTo("          000007");
    assertThat(InterestCalculator.tranId("20220718001", 1)).isEqualTo("2022071800000001");
    assertThat(InterestCalculator.tranId("2022071800", 1_000_000)).isEqualTo("2022071800000000");
  }

  @Test
  void db2TimestampKeepsHundredthsOnly() {
    assertThat(InterestCalculator.db2Timestamp(LocalDateTime.of(2022, 7, 18, 12, 0, 0)))
        .isEqualTo("2022-07-18-12.00.00.000000");
    assertThat(InterestCalculator.db2Timestamp(LocalDateTime.of(2022, 7, 18, 9, 5, 7, 987_654_321)))
        .isEqualTo("2022-07-18-09.05.07.980000");
  }

  @Test
  void interestTransactionLayout() {
    TransactionRecord t =
        InterestCalculator.interestTransaction(
            "2022071800000001",
            1L,
            new BigDecimal("14.55"),
            "9680294154603697",
            "2022-07-18-12.00.00.000000");
    String line = CoreCodecs.TRANSACTION.encodeLine(t);
    assertThat(line).hasSize(350);
    assertThat(line).startsWith("2022071800000001010005System    Int. for a/c 00000000001 ");
    assertThat(t.tranTypeCd()).isEqualTo("01");
    assertThat(t.tranCatCd()).isEqualTo(5);
    assertThat(t.tranMerchantId()).isZero();
    assertThat(t.tranMerchantName()).isBlank();
    assertThat(t.tranCardNum()).isEqualTo("9680294154603697");
    assertThat(t.tranOrigTs()).isEqualTo(t.tranProcTs());
  }

  @Test
  void padMovesPicX() {
    assertThat(InterestCalculator.pad("DEFAULT", 10)).isEqualTo("DEFAULT   ");
    assertThat(InterestCalculator.pad("ABCDEFGHIJKL", 10)).isEqualTo("ABCDEFGHIJ");
    assertThat(InterestCalculator.pad(null, 2)).isEqualTo("  ");
  }
}
