package com.carddemo.platform.math;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.platform.math.CobolMath.Mode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CobolMathTest {

  @ParameterizedTest(name = "{0} -> trunc {1}, rounded {2}")
  @CsvSource({"1.235,1.23,1.24", "-1.235,-1.23,-1.24", "0.004,0.00,0.00", "0.005,0.00,0.01"})
  void computeTruncatesUnlessRounded(String in, String trunc, String rounded) {
    BigDecimal v = new BigDecimal(in);
    assertThat(CobolMath.store(v, 9, 2, Mode.TRUNCATE)).isEqualTo(new BigDecimal(trunc));
    assertThat(CobolMath.store(v, 9, 2, Mode.ROUNDED)).isEqualTo(new BigDecimal(rounded));
  }

  @Test
  void moveToSmallerPicDropsHighOrderDigits() {
    assertThat(CobolMath.fit(new BigDecimal("123456.78"), 4, 2))
        .isEqualTo(new BigDecimal("3456.78"));
    assertThat(CobolMath.fit(new BigDecimal("-123456.789"), 4, 2))
        .isEqualTo(new BigDecimal("-3456.78"));
  }

  @Test
  void intcalcFormulaTruncates() {
    // CBACT04C: COMPUTE WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200 (no ROUNDED)
    BigDecimal interest =
        CobolMath.divide(
            new BigDecimal("1234.56").multiply(new BigDecimal("15.00")),
            new BigDecimal("1200"),
            9,
            2,
            Mode.TRUNCATE);
    assertThat(interest).isEqualTo(new BigDecimal("15.43"));
  }

  @Test
  void sizeError() {
    assertThat(CobolMath.sizeError(new BigDecimal("12345"), 4)).isTrue();
    assertThat(CobolMath.sizeError(new BigDecimal("1234.99"), 4)).isFalse();
  }
}
