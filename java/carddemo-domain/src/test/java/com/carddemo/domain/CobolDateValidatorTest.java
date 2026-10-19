package com.carddemo.domain;

import com.carddemo.domain.util.CobolDateValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CobolDateValidatorTest {

    @Test
    void reportsValidDateWithExactMessageLayout() {
        CobolDateValidator.Result result =
                CobolDateValidator.validate("2024-02-29", "YYYY-MM-DD");

        assertThat(result.severity()).isZero();
        assertThat(result.messageNumber()).isZero();
        assertThat(result.resultText()).isEqualTo("Date is valid  ");
        assertThat(result.message()).hasSize(80);
        assertThat(result.message())
                .isEqualTo("0000Mesg Code: 0000 Date is valid   TstDate: 2024-02-29 Mask used:YYYY-MM-DD    ");
    }

    @Test
    void reportsInvalidMonthWithExactMessageLayout() {
        CobolDateValidator.Result result =
                CobolDateValidator.validate("2024-13-01", "YYYY-MM-DD");

        assertThat(result.severity()).isEqualTo(3);
        assertThat(result.messageNumber()).isEqualTo(2517);
        assertThat(result.resultText()).isEqualTo("Invalid month  ");
        assertThat(result.message()).hasSize(80);
        assertThat(result.message())
                .isEqualTo("0003Mesg Code: 2517 Invalid month   TstDate: 2024-13-01 Mask used:YYYY-MM-DD    ");
    }

    @Test
    void mapsNonNumericAndInsufficientDates() {
        CobolDateValidator.Result nonNumeric =
                CobolDateValidator.validate("2024-ab-01", "YYYY-MM-DD");
        CobolDateValidator.Result insufficient =
                CobolDateValidator.validate("", "YYYY-MM-DD");

        assertThat(nonNumeric.messageNumber()).isEqualTo(2520);
        assertThat(nonNumeric.resultText()).isEqualTo("Nonnumeric data");
        assertThat(insufficient.messageNumber()).isEqualTo(2507);
        assertThat(insufficient.resultText()).isEqualTo("Insufficient   ");
    }
}
