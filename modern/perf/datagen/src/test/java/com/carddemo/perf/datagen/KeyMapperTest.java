package com.carddemo.perf.datagen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class KeyMapperTest {

  @Test
  void copyZeroIsIdentityAndOtherCopiesAvoidSamplePrefixes() {
    KeyMapper m = new KeyMapper(List.of("9001000000000001", "1000000000000002"), 3);
    assertThat(m.cardNum(0, "9001000000000001")).isEqualTo("9001000000000001");
    assertThat(m.cardNum(1, "9001000000000001")).isEqualTo("1001000000000001");
    assertThat(m.cardNum(2, "1000000000000002")).isEqualTo("1002000000000002");
    assertThat(m.acctId(0, 7)).isEqualTo(7);
    assertThat(m.acctId(999, 50)).isEqualTo(999_000_050L);
    assertThat(m.custId(999, 50)).isEqualTo(9_990_050);
  }

  @Test
  void rejectsSamplesWhoseCardTailsCollide() {
    assertThatThrownBy(() -> new KeyMapper(List.of("1111000000000001", "2222000000000001"), 2))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsScalesBeyondTheFreeCardPrefixes() {
    List<String> sample =
        IntStream.range(0, 100).mapToObj(i -> "%04d%012d".formatted(1000 + i, i)).toList();
    assertThat(new KeyMapper(sample, 8_901).cardNum(8_900, sample.get(0))).startsWith("9999");
    assertThatThrownBy(() -> new KeyMapper(sample, 8_902))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("8901 free card prefixes");
  }
}
