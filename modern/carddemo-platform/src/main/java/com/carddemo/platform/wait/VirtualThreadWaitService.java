package com.carddemo.platform.wait;

import com.carddemo.contracts.port.WaitService;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Production {@link WaitService} for {@code MVSWAIT} ({@code STIMER WAIT,BINTVL=}): the interval is
 * in centiseconds (hundredths of a second). {@link #await} parks the calling thread, which is cheap
 * on a virtual thread; {@link #awaitAsync} schedules the wait on a virtual thread and returns
 * immediately so a batch step can stay non-blocking.
 */
public final class VirtualThreadWaitService implements WaitService, AutoCloseable {

  /** {@code MVSWAIT-TIME} is {@code PIC 9(8) COMP}: at most 99 999 999 centiseconds. */
  public static final long MAX_CENTISECONDS = 99_999_999L;

  private final Executor executor;
  private final ExecutorService owned;

  public VirtualThreadWaitService() {
    ExecutorService virtualThreads = Executors.newVirtualThreadPerTaskExecutor();
    this.executor = virtualThreads;
    this.owned = virtualThreads;
  }

  /** Uses a caller-managed executor, which {@link #close()} leaves running. */
  VirtualThreadWaitService(Executor executor) {
    this.executor = Objects.requireNonNull(executor, "executor");
    this.owned = null;
  }

  /** Interrupts pending asynchronous waits (called by Spring on context shutdown). */
  @Override
  public void close() {
    if (owned != null) {
      owned.shutdownNow();
    }
  }

  @Override
  public void await(Duration duration) throws InterruptedException {
    Objects.requireNonNull(duration, "duration");
    if (duration.isNegative()) {
      throw new IllegalArgumentException("negative wait: " + duration);
    }
    if (!duration.isZero()) {
      Thread.sleep(duration);
    }
  }

  @Override
  public void awaitCentiseconds(long centiseconds) throws InterruptedException {
    await(toDuration(centiseconds));
  }

  /** Non-blocking variant: completes after the interval, on a virtual thread. */
  public CompletableFuture<Void> awaitAsync(Duration duration) {
    return CompletableFuture.runAsync(
        () -> {
          try {
            await(duration);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CompletionException(e);
          }
        },
        executor);
  }

  public CompletableFuture<Void> awaitCentisecondsAsync(long centiseconds) {
    return awaitAsync(toDuration(centiseconds));
  }

  /** Centiseconds ({@code BINTVL} units) to a {@link Duration}; rejects out-of-PIC values. */
  public static Duration toDuration(long centiseconds) {
    if (centiseconds < 0 || centiseconds > MAX_CENTISECONDS) {
      throw new IllegalArgumentException("centiseconds out of PIC 9(8) range: " + centiseconds);
    }
    return Duration.ofMillis(centiseconds * 10);
  }
}
