package com.carddemo.platform.wait;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VirtualThreadWaitServiceTest {

  private final VirtualThreadWaitService service = new VirtualThreadWaitService();

  @ParameterizedTest
  @CsvSource({"0,0", "1,10", "100,1000", "3600,36000", "99999999,999999990"})
  void centisecondsAreHundredthsOfASecond(long centiseconds, long millis) {
    assertThat(VirtualThreadWaitService.toDuration(centiseconds))
        .isEqualTo(Duration.ofMillis(millis));
  }

  @ParameterizedTest
  @CsvSource({"-1", "100000000"})
  void rejectsValuesOutsidePic9of8(long centiseconds) {
    assertThatThrownBy(() -> service.awaitCentiseconds(centiseconds))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void awaitBlocksForTheInterval() throws Exception {
    long start = System.nanoTime();
    service.awaitCentiseconds(5);
    assertThat(System.nanoTime() - start).isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(50));
    service.await(Duration.ZERO);
    assertThatThrownBy(() -> service.await(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void asyncWaitRunsOnAVirtualThreadWithoutBlockingTheCaller() throws Exception {
    long start = System.nanoTime();
    var future = service.awaitCentisecondsAsync(10);
    assertThat(System.nanoTime() - start).isLessThan(TimeUnit.MILLISECONDS.toNanos(100));
    future.get(5, TimeUnit.SECONDS);
    assertThat(System.nanoTime() - start)
        .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(100));
  }

  @Test
  void interruptCancelsTheWait() throws Exception {
    Thread waiter =
        Thread.ofVirtual()
            .start(
                () -> {
                  try {
                    service.awaitCentiseconds(10_000);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                });
    waiter.interrupt();
    assertThat(waiter.join(Duration.ofSeconds(5))).isTrue();
  }
}
