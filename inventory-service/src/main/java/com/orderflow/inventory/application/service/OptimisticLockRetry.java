package com.orderflow.inventory.application.service;


import com.orderflow.common.exception.ConcurrencyConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;

/**
 * Re-runs  an operation when it fails due to an optimistic locking conflict.
 *
 * It must be applied outside transaction boundaries: each attempt must start a new
 * transaction to re-read the current state of the data.
 */
@Component
public class OptimisticLockRetry {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockRetry.class);

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final LongConsumer sleeper;


    @Autowired
    public OptimisticLockRetry(
            @Value("${inventory.retry.max-attempts:5}") int maxAttempts,
            @Value("${inventory.retry.initial-backoff-ms:50}") long initialBackoffMs,
            @Value("${inventory.retry.max-backoff-ms:500}") long maxBackoffMs
    ) {
        this(maxAttempts, initialBackoffMs, maxBackoffMs, OptimisticLockRetry::sleep);
    }


    /** Visible for tests: lets them avoid real sleeping. */
    OptimisticLockRetry(int maxAttempts, long initialBackoffMs, long maxBackoffMs, LongConsumer sleeper) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.sleeper = sleeper;
    }

    public void run(String operation, Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (OptimisticLockingFailureException | ConcurrencyConflictException ex) {
                if (attempt >= maxAttempts) {
                    throw new ConcurrencyConflictException(String.format(
                            "%s failed after %d attempts due to concurrent updates", operation, attempt), ex);
                }
                long delayMs = backoffWithJitter(attempt);
                log.warn("Optimistic lock conflict in {} (attempt {}/{}). Retrying in {} ms",
                        operation, attempt, maxAttempts, delayMs);
                sleeper.accept(delayMs);
            }
        }
    }

    /** Full jitter: random delay between 0 and min(max, initial * 2^(attempt-1)). */
    long backoffWithJitter(int attempt) {
        long ceiling = Math.min(maxBackoffMs, initialBackoffMs << Math.min(attempt - 1, 20));
        return ceiling <= 0 ? 0 : ThreadLocalRandom.current().nextLong(ceiling + 1);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry", ex);
        }
    }

}
