package com.orderflow.inventory.infrastructure.out.persistence;

import com.orderflow.common.exception.ConcurrencyConflictException;
import com.orderflow.inventory.domain.model.Product;
import com.orderflow.inventory.infrastructure.adapter.out.persistence.ProductPersistenceAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Multithreaded integration test verifying @Version optimistic locking behavior under contention.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProductConcurrencyTest {

    @Autowired
    private ProductPersistenceAdapter persistenceAdapter;

    @Test
    @DisplayName("Should detect optimistic locking conflict when two concurrent threads modify the same product version")
    void shouldDetectOptimisticLockingConflictUnderConcurrency() throws InterruptedException {

        // 1. Arrange: Create initial product with available stock
        Product initialProduct = new Product(
                "CONCURRENCY-SKU-001",
                "Concurrent Test Laptop",
                "High performance laptop for testing race conditions",
                new BigDecimal("1200.00"),
                100
        );

        Product savedProduct = persistenceAdapter.save(initialProduct);
        String productId = savedProduct.getId();

        // 2. Prepare 2 concurrent threads to simulate race condition
        int numberOfThreads = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CyclicBarrier barrier = new CyclicBarrier(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final int quantityToReserve = 10;
            executorService.submit(() -> {
                try {
                    // Both threads read the same initial state (same @Version)
                    Product threadProduct = persistenceAdapter.findById(productId).orElseThrow();
                    // Synchronize threads so both attempt writing at the exact same moment
                    barrier.await();
                    threadProduct.reserveStock(quantityToReserve);
                    persistenceAdapter.save(threadProduct);
                    successCount.incrementAndGet();
                } catch (ConcurrencyConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // Ignore other exceptions
                } finally {
                    latch.countDown();
                }
            });

        }

        // 3. Await completion of both threads
        latch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // 4. Assert: Exactly 1 thread must succeed and 1 thread must trigger optimistic conflict
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Verify the database state reflects only the successful update
        Product finalProduct = persistenceAdapter.findById(productId).orElseThrow();
        assertThat(finalProduct.getAvailableQuantity()).isEqualTo(90);
        assertThat(finalProduct.getReservedQuantity()).isEqualTo(10);

    }

}