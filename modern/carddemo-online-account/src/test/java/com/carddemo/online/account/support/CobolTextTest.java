package com.carddemo.online.account.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Expected values verified with GnuCOBOL 3.1.2 (-std=ibm). */
class CobolTextTest {

  @Test
  void formatAmountMatchesPicout() {
    assertThat(CobolText.formatAmount(new BigDecimal("194.00"))).isEqualTo("+        194.00");
    assertThat(CobolText.formatAmount(BigDecimal.ZERO)).isEqualTo("+           .00");
    assertThat(CobolText.formatAmount(new BigDecimal("-1234567.5"))).isEqualTo("-  1,234,567.50");
    assertThat(CobolText.formatAmount(new BigDecimal("12345678901.23")))
        .isEqualTo("+345,678,901.23");
  }

  @Test
  void numvalC() {
    assertThat(CobolText.testNumvalC(" 1,234.567 ")).isTrue();
    assertThat(CobolText.toS9v2(CobolText.numvalC(" 1,234.567 "))).isEqualByComparingTo("1234.56");
    assertThat(CobolText.numvalC("$12.5CR")).isEqualByComparingTo("-12.5");
    assertThat(CobolText.numvalC("12-")).isEqualByComparingTo("-12");
    assertThat(CobolText.numvalC("- 5")).isEqualByComparingTo("-5");
    assertThat(CobolText.numvalC("12DB")).isEqualByComparingTo("-12");
    assertThat(CobolText.numvalC("+5")).isEqualByComparingTo("5");
    for (String ok : new String[] {"+        194.00", "1,2,3", ".5", "5."}) {
      assertThat(CobolText.testNumvalC(ok)).as(ok).isTrue();
    }
    for (String ko : new String[] {"1 2", "abc", "", ".", "-5-", null}) {
      assertThat(CobolText.testNumvalC(ko)).as(String.valueOf(ko)).isFalse();
    }
    assertThat(CobolText.testNumval(" 1")).isTrue();
    assertThat(CobolText.numval("12")).isEqualByComparingTo("12");
    assertThat(CobolText.testNumval("$1")).isFalse();
  }

  @Test
  void alphanumericHelpers() {
    assertThat(CobolText.isBlank(null)).isTrue();
    assertThat(CobolText.isBlank(" \0 ")).isTrue();
    assertThat(CobolText.isBlank(" a")).isFalse();
    assertThat(CobolText.pad("ab", 4)).isEqualTo("ab  ");
    assertThat(CobolText.pad("abcdef", 4)).isEqualTo("abcd");
    assertThat(CobolText.pad(null, 2)).isEqualTo("  ");
    assertThat(CobolText.rightJustify("1", 2)).isEqualTo(" 1");
    assertThat(CobolText.rightJustify("12345", 4)).isEqualTo("2345");
    assertThat(CobolText.isNumeric("0123")).isTrue();
    assertThat(CobolText.isNumeric(" 123")).isFalse();
    assertThat(CobolText.isNumeric("")).isFalse();
    assertThat(CobolText.isNumeric(null)).isFalse();
    assertThat(CobolText.upperTrim(" ab ")).isEqualTo("AB");
    assertThat(CobolText.upperTrim(null)).isEmpty();
    assertThat(CobolText.display("ab  ")).isEqualTo("ab");
    assertThat(CobolText.display("   ")).isNull();
    assertThat(CobolText.display(null)).isNull();
    assertThat(CobolText.zoned(42, 5)).isEqualTo("00042");
    assertThat(CobolText.zoned(123456, 3)).isEqualTo("456");
  }

  @Test
  void lookupTablesFromCslkpcdy() {
    assertThat(LookupCodes.GENERAL_PURPOSE_AREA_CODES).hasSize(410).contains("908");
    assertThat(LookupCodes.US_STATE_CODES).hasSize(56).contains("NY", "NC");
    assertThat(LookupCodes.US_STATE_ZIP2_COMBOS).hasSize(240).contains("NY12");
    assertThat(LookupCodes.isUsStateCode("ny")).isFalse();
    assertThat(LookupCodes.isGeneralPurposeAreaCode("373")).isFalse();
    assertThat(LookupCodes.isUsStateZip2Combo("NC12")).isFalse();
  }
}
