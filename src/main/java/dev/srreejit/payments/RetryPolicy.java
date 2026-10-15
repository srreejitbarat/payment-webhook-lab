package dev.srreejit.payments;

public final class RetryPolicy {
    private RetryPolicy() {}
    public static long delayMs(int failedAttempts) {
        if (failedAttempts < 1) throw new IllegalArgumentException("failedAttempts must be positive");
        return Math.min(60_000L, 1_000L << Math.min(failedAttempts - 1, 6));
    }
}
