package com.carddemo.domain;
import com.carddemo.domain.util.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
class ZonedDecimalCodecTest {
 @Test void decodesVerifiedValues(){assertThat(ZonedDecimalCodec.decode("0000005047G")).isEqualByComparingTo("504.77");assertThat(ZonedDecimalCodec.decode("0000009190}")).isEqualByComparingTo("-919.00");assertThat(ZonedDecimalCodec.decode("00150{")).isEqualByComparingTo("15.00");}
 @Test void preservesScaleAndRoundTrips(){BigDecimal x=ZonedDecimalCodec.decode("0000005047G");assertThat(x.scale()).isEqualTo(2);assertThat(ZonedDecimalCodec.decode(ZonedDecimalCodec.encode(x,11))).isEqualByComparingTo(x);assertThat(ZonedDecimalCodec.decode("0000000012J")).isEqualByComparingTo("-1.21");}
}
