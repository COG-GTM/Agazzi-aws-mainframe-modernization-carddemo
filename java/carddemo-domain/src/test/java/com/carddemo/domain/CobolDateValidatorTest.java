package com.carddemo.domain;
import com.carddemo.domain.util.CobolDateValidator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class CobolDateValidatorTest {
 @Test void validatesAndReportsErrors(){assertThat(CobolDateValidator.validate("2024-02-29","YYYY-MM-DD").severity()).isZero();assertThat(CobolDateValidator.validate("2024-13-01","YYYY-MM-DD").resultText()).isEqualTo("Invalid month  ");assertThat(CobolDateValidator.validate("2024-ab-01","YYYY-MM-DD").resultText()).isEqualTo("Nonnumeric data");assertThat(CobolDateValidator.validate("","YYYY-MM-DD").resultText()).isEqualTo("Insufficient");assertThat(CobolDateValidator.validate("2024-02-29","YYYY-MM-DD").message()).hasSize(80);}
}
