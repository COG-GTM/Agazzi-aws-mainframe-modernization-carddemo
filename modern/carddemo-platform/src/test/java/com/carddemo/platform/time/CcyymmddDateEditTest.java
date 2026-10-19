package com.carddemo.platform.time;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.platform.time.CcyymmddDateEdit.Flag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Table derived from the paragraphs of {@code app/cpy/CSUTLDPY.cpy}. */
class CcyymmddDateEditTest {

  private final CcyymmddDateEdit edit = new CcyymmddDateEdit(FixedBusinessClock.golden());

  @ParameterizedTest(name = "''{0}''")
  @CsvSource(
      delimiter = '|',
      value = {
        "20220718|VALID|VALID|VALID|''",
        "19000101|VALID|VALID|VALID|''",
        "20991231|VALID|VALID|VALID|''",
        "20000229|VALID|VALID|VALID|''",
        "20240229|VALID|VALID|VALID|''",
        "'        '|BLANK|BLANK|BLANK|Open Date : Year must be supplied.",
        "'    0718'|BLANK|VALID|VALID|Open Date : Year must be supplied.",
        "20AB0718|NOT_OK|VALID|VALID|Open Date must be 4 digit number.",
        "18990718|NOT_OK|VALID|VALID|Open Date : Century is not valid.",
        "21000718|NOT_OK|VALID|VALID|Open Date : Century is not valid.",
        "2022  18|VALID|BLANK|VALID|Open Date : Month must be supplied.",
        "20221318|VALID|NOT_OK|VALID|Open Date: Month must be a number between 1 and 12.",
        "20220018|VALID|NOT_OK|VALID|Open Date: Month must be a number between 1 and 12.",
        "202207  |VALID|VALID|BLANK|Open Date : Day must be supplied.",
        "20220732|VALID|VALID|NOT_OK|Open Date:day must be a number between 1 and 31.",
        "202207X1|VALID|VALID|NOT_OK|Open Date:day must be a number between 1 and 31.",
        "20220431|VALID|NOT_OK|NOT_OK|Open Date:Cannot have 31 days in this month.",
        "20220230|VALID|NOT_OK|NOT_OK|Open Date:Cannot have 30 days in this month.",
        "20230229|NOT_OK|NOT_OK|NOT_OK|Open Date:Not a leap year.Cannot have 29 days in this month.",
        "19000229|NOT_OK|NOT_OK|NOT_OK|Open Date:Not a leap year.Cannot have 29 days in this month.",
        // first message wins
        "18991399|NOT_OK|NOT_OK|NOT_OK|Open Date : Century is not valid."
      })
  void editsLikeTheCopybook(String date, Flag year, Flag month, Flag day, String message) {
    CcyymmddDateEdit.Result result = edit.editCcyymmdd("Open Date ", date);

    assertThat(result.year()).isEqualTo(year);
    assertThat(result.month()).isEqualTo(month);
    assertThat(result.day()).isEqualTo(day);
    assertThat(result.returnMessage()).isEqualTo(message);
    assertThat(result.inputError()).isEqualTo(!message.isEmpty());
    assertThat(result.valid()).isEqualTo(message.isEmpty());
  }

  @ParameterizedTest(name = "DOB ''{0}''")
  @CsvSource(
      delimiter = '|',
      value = {
        "19800101|''",
        "20220717|''",
        "20220718|'Date of Birth:cannot be in the future '",
        "20300101|'Date of Birth:cannot be in the future '",
        "20221301|Date of Birth: Month must be a number between 1 and 12."
      })
  void dateOfBirthMustBeInThePast(String date, String message) {
    CcyymmddDateEdit.Result result = edit.editDateOfBirth("Date of Birth", date);

    assertThat(result.returnMessage()).isEqualTo(message);
    assertThat(result.valid()).isEqualTo(message.isEmpty());
  }
}
