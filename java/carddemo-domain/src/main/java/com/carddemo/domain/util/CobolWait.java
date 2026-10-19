package com.carddemo.domain.util;

/**
 * MVS ASMWAIT-compatible wait.
 *
 * <p>The default is a no-op because Spring Batch sequencing replaces JCL
 * pacing steps. Tests or local demonstrations can opt into real sleeping.</p>
 */
public final class CobolWait {

    private CobolWait() {
    }

    public static void waitCentiseconds(long value) {
        waitCentiseconds(value, false);
    }

    public static void waitCentiseconds(long value, boolean realSleep) {
        if (!realSleep || value <= 0) {
            return;
        }
        try {
            Thread.sleep(value * 10L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
