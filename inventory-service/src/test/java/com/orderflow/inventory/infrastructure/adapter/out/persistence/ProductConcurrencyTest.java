package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import com.orderflow.common.exception.ConcurrencyConflictException;
import com.orderflow.inventory.application.port.in.StockReservationUseCase;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ConfirmStockCommand;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.OrderItemRequest;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ReleaseStockCommand;
import com.orderflow.inventory.application.port.in.StockReservationUseCase.ReserveStockCommand;
import com.orderflow.inventory.application.port.out.ProcessedOrderReservationPort;
import com.orderflow.inventory.application.port.out.ReservationRepositoryPort;
import com.orderflow.inventory.application.service.RetryingStockReservationService;
import com.orderflow.inventory.domain.model.Product;
import com.orderflow.inventory.domain.model.ReservationStatus;
import com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox.InventoryOutboxEntity;
import com.orderflow.inventory.infrastructure.adapter.out.messaging.outbox.InventoryOutboxRepository;
import com.orderflow.inventory.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Multithreaded integration tests against a real PostgreSQL.
 * Outcomes are asserted on database state, which is the source of truth.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.relay.enabled=false",
        "inventory.retry.max-attempts=60",
        "inventory.retry.initial-backoff-ms=5",
        "inventory.retry.max-backoff-ms=50"
})
@ActiveProfiles("test")
class ProductConcurrencyTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private ProductPersistenceAdapter productRepository;

    @Autowired
    private StockReservationUseCase stockReservation;

    @Autowired
    private ReservationRepositoryPort reservations;

    @Autowired
    private ProcessedOrderReservationPort processedOrderReservationPort;

    @Autowired
    private InventoryOutboxRepository outbox;

    @Test
    @DisplayName("The use case injected into inbound adapters is the retrying decorator")
    void useCaseIsRetryingDecorator() {
        assertThat(stockReservation).isInstanceOf(RetryingStockReservationService.class);
    }

    @Test
    @DisplayName("Should persist processed order reservations for redelivery deduplication")
    void shouldPersistProcessedOrderReservation() {
        String orderId = UUID.randomUUID().toString();

        assertThat(processedOrderReservationPort.existsByOrderId(orderId)).isFalse();

        processedOrderReservationPort.markProcessed(orderId);

        assertThat(processedOrderReservationPort.existsByOrderId(orderId)).isTrue();
    }

    @Test
    @DisplayName("Two writers holding the same @Version: exactly one wins, the other gets a conflict")
    void optimisticLockingDetectsStaleWrite() throws Exception {
        Product saved = newProduct(100);
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<Throwable> failures = runConcurrently(2, i -> {
            try {
                Product snapshot = productRepository.findById(saved.getId()).orElseThrow();
                barrier.await(10, TimeUnit.SECONDS);   // both hold the same version
                snapshot.reserveStock(10);
                productRepository.save(snapshot);
            } catch (ConcurrencyConflictException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });

        assertThat(failures).hasSize(1);
        assertThat(failures.getFirst()).isInstanceOf(ConcurrencyConflictException.class);

        Product finalState = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(finalState.getAvailableQuantity()).isEqualTo(90);
        assertThat(finalState.getReservedQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("50 concurrent orders on one product never oversell and never lose a reservation")
    void concurrentReservationsNeverOversell() throws Exception {
        int stock = 100;
        int quantityPerOrder = 3;
        int orders = 50;
        int expectedReserved = stock / quantityPerOrder;   // 33
        Product product = newProduct(stock);
        List<String> orderIds = orderIds(orders);

        List<Throwable> failures = runConcurrently(orders, i ->
                stockReservation.reserveStock(new ReserveStockCommand(
                        orderIds.get(i),
                        List.of(new OrderItemRequest(product.getId(), quantityPerOrder)))));

        assertThat(failures).isEmpty();

        long reservedOrders = orderIds.stream().filter(this::isReserved).count();
        assertThat(reservedOrders).isEqualTo(expectedReserved);

        Product finalState = productRepository.findById(product.getId()).orElseThrow();
        assertThat(finalState.getReservedQuantity()).isEqualTo(expectedReserved * quantityPerOrder);
        assertThat(finalState.getAvailableQuantity()).isEqualTo(stock - expectedReserved * quantityPerOrder);
        assertThat(finalState.getAvailableQuantity() + finalState.getReservedQuantity()).isEqualTo(stock);

        // Orders that didn't fit are rejected through the normal path (no product write, so no conflict)
        // Exactly one event per order: attempts rolled back by a conflict leave nothing behind
        List<InventoryOutboxEntity> events = eventsFor(orderIds);
        assertThat(events).hasSize(orders);
        assertThat(countOfType(events, "StockReservedEvent")).isEqualTo(expectedReserved);
        assertThat(countOfType(events, "StockRejectedEvent")).isEqualTo(orders - expectedReserved);
    }

    @Test
    @DisplayName("Concurrent and duplicated confirm/release keep available + reserved consistent")
    void concurrentConfirmAndReleaseAreIdempotent() throws Exception {
        int stock = 100;
        int orders = 20;
        int quantityPerOrder = 2;
        Product product = newProduct(stock);
        List<String> orderIds = orderIds(orders);

        for (String orderId : orderIds) {
            stockReservation.reserveStock(new ReserveStockCommand(
                    orderId, List.of(new OrderItemRequest(product.getId(), quantityPerOrder))));
        }
        Product afterReserve = productRepository.findById(product.getId()).orElseThrow();
        assertThat(afterReserve.getReservedQuantity()).isEqualTo(orders * quantityPerOrder);

        // First half is confirmed, second half is released. Every operation is delivered twice.
        List<Throwable> failures = runConcurrently(orders * 2, i -> {
            int orderIndex = i % orders;
            String orderId = orderIds.get(orderIndex);
            if (orderIndex < orders / 2) {
                stockReservation.confirmStock(new ConfirmStockCommand(orderId));
            } else {
                stockReservation.releaseStock(new ReleaseStockCommand(orderId));
            }
        });

        assertThat(failures).isEmpty();

        int confirmedUnits = (orders / 2) * quantityPerOrder;   // leave the warehouse for good
        Product finalState = productRepository.findById(product.getId()).orElseThrow();
        assertThat(finalState.getReservedQuantity()).isZero();
        assertThat(finalState.getAvailableQuantity()).isEqualTo(stock - confirmedUnits);
    }

    @Test
    @DisplayName("The same OrderCreated delivered concurrently reserves stock only once")
    void duplicatedOrderDeliveryReservesOnce() throws Exception {
        Product product = newProduct(100);
        String orderId = UUID.randomUUID().toString();
        int deliveries = 10;

        List<Throwable> failures = runConcurrently(deliveries, i ->
                stockReservation.reserveStock(new ReserveStockCommand(
                        orderId, List.of(new OrderItemRequest(product.getId(), 5)))));

        // Losers of the race fail on the processed-order primary key; Kafka would redeliver them
        // and they'd then be ignored as duplicates.
        assertThat(failures).allSatisfy(t -> assertThat(t).isInstanceOf(DataAccessException.class));

        Product finalState = productRepository.findById(product.getId()).orElseThrow();
        assertThat(finalState.getReservedQuantity()).isEqualTo(5);
        assertThat(finalState.getAvailableQuantity()).isEqualTo(95);
        assertThat(isReserved(orderId)).isTrue();
        assertThat(eventsFor(List.of(orderId))).hasSize(1);
    }

    // ---------- helpers ----------

    private Product newProduct(int stock) {
        return productRepository.save(new Product(
                "SKU-" + UUID.randomUUID(),
                "Contended product",
                "Product used in concurrency tests",
                new BigDecimal("10.00"),
                stock));
    }

    private List<String> orderIds(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID().toString()).toList();
    }

    private boolean isReserved(String orderId) {
        return reservations.findByOrderId(orderId)
                .filter(r -> r.getStatus() == ReservationStatus.RESERVED)
                .isPresent();
    }

    /**
     * Runs {@code task} on {@code threads} threads released at the same instant and returns
     * every exception thrown. A hang fails the test instead of blocking it forever.
     */
    private static List<Throwable> runConcurrently(int threads, IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Throwable>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                int index = i;
                Callable<Throwable> job = () -> {
                    ready.countDown();
                    start.await();
                    try {
                        task.accept(index);
                        return null;
                    } catch (Throwable t) {
                        return t;
                    }
                };
                futures.add(pool.submit(job));
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("all threads ready").isTrue();
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get(60, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<InventoryOutboxEntity> eventsFor(List<String> orderIds) {
        return outbox.findAll().stream()
                .filter(e -> orderIds.contains(e.getMessageKey()))
                .toList();
    }

    private long countOfType(List<InventoryOutboxEntity> events, String eventType) {
        return events.stream().filter(e -> e.getPayload().contains("\"" + eventType + "\"")).count();
    }


}