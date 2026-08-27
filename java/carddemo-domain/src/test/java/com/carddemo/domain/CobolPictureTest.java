package com.carddemo.domain;

import com.carddemo.domain.util.CobolPicture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CobolPictureTest {

    @Test
    void formatsLeadingSignPicture() {
        assertThat(CobolPicture.signedLeading(new BigDecimal("1234.56")))
                .isEqualTo("       1,234.56");
        assertThat(CobolPicture.signedLeading(new BigDecimal("-1234.56")))
                .isEqualTo("-      1,234.56");
        assertThat(CobolPicture.signedLeading(new BigDecimal("0.56")))
                .isEqualTo("            .56");
        assertThat(CobolPicture.signedLeading(BigDecimal.ZERO))
                .isEqualTo(" ".repeat(15));
    }

    @Test
    void formatsAlwaysSignedLeadingPicture() {
        assertThat(CobolPicture.signedLeadingAlways(new BigDecimal("1234.56")))
                .isEqualTo("+      1,234.56");
        assertThat(CobolPicture.signedLeadingAlways(new BigDecimal("-1234.56")))
                .isEqualTo("-      1,234.56");
        assertThat(CobolPicture.signedLeadingAlways(new BigDecimal("0.56")))
                .isEqualTo("+           .56");
        assertThat(CobolPicture.signedLeadingAlways(BigDecimal.ZERO))
                .isEqualTo(" ".repeat(15));
    }

    @Test
    void formatsTrailingZPicture() {
        assertThat(CobolPicture.signedTrailing(new BigDecimal("1234.56")))
                .isEqualTo("     1234.56 ");
        assertThat(CobolPicture.signedTrailing(new BigDecimal("-1234.56")))
                .isEqualTo("     1234.56-");
        assertThat(CobolPicture.signedTrailing(new BigDecimal("0.56")))
                .isEqualTo("         .56 ");
        assertThat(CobolPicture.signedTrailing(BigDecimal.ZERO))
                .isEqualTo("         .00 ");
    }

    @Test
    void formatsTrailingNinePictureAndMaximumWidth() {
        assertThat(CobolPicture.unsignedTrailingSign(new BigDecimal("1234.56")))
                .isEqualTo("000001234.56 ");
        assertThat(CobolPicture.unsignedTrailingSign(new BigDecimal("-1234.56")))
                .isEqualTo("000001234.56-");
        assertThat(CobolPicture.unsignedTrailingSign(BigDecimal.ZERO))
                .isEqualTo("000000000.00 ");
        assertThat(CobolPicture.unsignedTrailingSign(new BigDecimal("999999999.99")))
                .isEqualTo("999999999.99 ");
    }

    @Test
    void preservesPictureWidths() {
        for (BigDecimal value : new BigDecimal[] {
                BigDecimal.ZERO,
                new BigDecimal("0.56"),
                new BigDecimal("-1234.56"),
                new BigDecimal("999999999.99")
        }) {
            assertThat(CobolPicture.signedLeading(value)).hasSize(15);
            assertThat(CobolPicture.signedLeadingAlways(value)).hasSize(15);
            assertThat(CobolPicture.signedTrailing(value)).hasSize(13);
            assertThat(CobolPicture.unsignedTrailingSign(value)).hasSize(13);
        }
    }
}
