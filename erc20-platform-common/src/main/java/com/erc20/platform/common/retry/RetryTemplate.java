package com.erc20.platform.common.retry;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

public class RetryTemplate {

    private final int maxRetries;
    private final long initialBackoffMs;
    private final double multiplier;
    private final Set<Class<? extends Throwable>> retryableExceptions;

    public RetryTemplate(int maxRetries, long initialBackoffMs, double multiplier,
                         Set<Class<? extends Throwable>> retryableExceptions) {
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.multiplier = multiplier;
        this.retryableExceptions = retryableExceptions;
    }

    public RetryTemplate(int maxRetries, long initialBackoffMs) {
        this(maxRetries, initialBackoffMs, 2.0, Collections.singleton(Exception.class));
    }

    public <T> T execute(Supplier<T> action) {
        int attempt = 0;
        long backoff = initialBackoffMs;

        while (true) {
            try {
                return action.get();
            } catch (Exception e) {
                attempt++;
                if (attempt > maxRetries || !isRetryable(e)) {
                    throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
                }
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                backoff = (long) (backoff * multiplier);
            }
        }
    }

    public void executeVoid(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }

    private boolean isRetryable(Exception e) {
        for (Class<? extends Throwable> clazz : retryableExceptions) {
            if (clazz.isAssignableFrom(e.getClass())) {
                return true;
            }
        }
        return false;
    }
}
