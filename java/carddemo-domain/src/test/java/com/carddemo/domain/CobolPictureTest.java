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
        assertThat(CobolPicture.signedLeading(BigDecimal.ZERO))
                .isEqualTo("           0.00");
    }

    @Test
    void formatsAlwaysSignedLeadingPicture() {
        assertThat(CobolPicture.signedLeadingAlways(new BigDecimal("1234.56")))
                .isEqualTo("+      1,234.56");
        assertThat(CobolPicture.signedLeadingAlways(new BigDecimal("-1234.56")))
                .isEqualTo("-      1,234.56");
        assertThat(CobolPicture.signedLeadingAlways(BigDecimal.ZERO))
                .isEqualTo("+          0.00");
    }

    @Test
    void formatsTrailingZPicture() {
        assertThat(CobolPicture.signedTrailing(new BigDecimal("1234.56")))
                .isEqualTo("     1234.56 ");
        assertThat(CobolPicture.signedTrailing(new BigDecimal("-1234.56")))
                .isEqualTo("     1234.56-");
        assertThat(CobolPicture.signedTrailing(BigDecimal.ZERO))
                .isEqualTo("        0.00 ");
    }

    @Test
    void formatsTrailingNinePictureAndMaximumWidth() {
        assertThat(CobolPicture.unsignedTrailingSign(new BigDecimal("1234.56")))
                .isEqualTo("000001234.56 ");
        assertThat(CobolPicture.unsignedTrailingSign(new BigDecimal("-1234.56")))
                .isEqualTo("000001234.56-");
        assertThat(CobolPicture.unsignedTrailingSign(new BigDecimal("999999999.99")))
                .isEqualTo("999999999.99 ");
    }
}
