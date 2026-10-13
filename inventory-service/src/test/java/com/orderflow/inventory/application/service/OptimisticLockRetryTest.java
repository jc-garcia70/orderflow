package com.orderflow.inventory.application.service;

import com.orderflow.common.exception.ConcurrencyConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OptimisticLockRetryTest {

    private final List<Long> sleeps = new ArrayList<>();

    private OptimisticLockRetry retry(int maxAttempts) {
        return new OptimisticLockRetry(maxAttempts, 50, 500, sleeps::add);
    }

    @Test
    @DisplayName("Succeeds on the first attempt without sleeping")
    void succeedsImmediately() {
        AtomicInteger calls = new AtomicInteger();

        retry(5).run("op", calls::incrementAndGet);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("Retries after optimistic lock failures until it succeeds")
    void retriesUntilSuccess() {
        AtomicInteger calls = new AtomicInteger();

        retry(5).run("op", () -> {
            if (calls.incrementAndGet() < 3) {
                throw new OptimisticLockingFailureException("conflict");
            }
        });

        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    @DisplayName("Also retries ConcurrencyConflictException raised by the persistence adapter")
    void retriesDomainConflict() {
        AtomicInteger calls = new AtomicInteger();

        retry(5).run("op", () -> {
            if (calls.incrementAndGet() < 2) {
                throw new ConcurrencyConflictException("conflict");
            }
        });

        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("Throws ConcurrencyConflictException once attempts are exhausted")
    void exhaustsAttempts() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry(3).run("reserveStock(order=ord-1)", () -> {
            calls.incrementAndGet();
            throw new OptimisticLockingFailureException("conflict");
        }))
                .isInstanceOf(ConcurrencyConflictException.class)
                .hasMessageContaining("reserveStock(order=ord-1)")
                .hasMessageContaining("3 attempts")
                .hasCauseInstanceOf(OptimisticLockingFailureException.class);

        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasSize(2);
    }

    @Test
    @DisplayName("Does not retry unrelated exceptions")
    void doesNotRetryOtherFailures() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> retry(5).run("op", () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("database down");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(calls).hasValue(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("Backoff stays within [0, min(max, initial * 2^(attempt-1))]")
    void backoffIsBounded() {
        OptimisticLockRetry retry = retry(10);

        for (int i = 0; i < 200; i++) {
            assertThat(retry.backoffWithJitter(1)).isBetween(0L, 50L);
            assertThat(retry.backoffWithJitter(3)).isBetween(0L, 200L);
            assertThat(retry.backoffWithJitter(9)).isBetween(0L, 500L);
        }
    }
}