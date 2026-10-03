package com.orderflow.inventory.infrastructure.config;

import com.orderflow.inventory.application.port.out.ProductRepositoryPort;
import com.orderflow.inventory.domain.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds initial product catalog if the database is currently empty.
 * Ensures developer-friendly startup with sample inventory ready for orders.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initInventory(ProductRepositoryPort productRepositoryPort) {
        return args -> {
            if (productRepositoryPort.findAll(Pageable.ofSize(1)).isEmpty()) {

                log.info("Inventory table is empty. Seeding initial tech catalog...");

                List<Product> initialProducts = List.of(
                        new Product(
                                "PROD-LAPTOP-001",
                                "MacBook Pro 16 M3 Max",
                                "Apple M3 Max, 36GB RAM, 1TB SSD, Space Black",
                                new BigDecimal("2499.99"),
                                25
                        ),
                        new Product(
                                "PROD-MOUSE-002",
                                "Logitech MX Master 3S",
                                "Advanced wireless mouse with quiet clicks and 8K DPI",
                                new BigDecimal("99.99"),
                                100
                        ),
                        new Product(
                                "PROD-KEYBOARD-003",
                                "Keychron Q1 Pro Mechanical Keyboard",
                                "Wireless custom mechanical keyboard, QMK/VIA support",
                                new BigDecimal("199.99"),
                                75
                        ),
                        new Product(
                                "PROD-MONITOR-004",
                                "Dell UltraSharp 27 4K USB-C Hub",
                                "27-inch 4K UHD IPS monitor with 90W Power Delivery",
                                new BigDecimal("549.99"),
                                40
                        ),
                        new Product(
                                "PROD-HEADSET-005",
                                "Sony WH-1000XM5 Wireless Headphones",
                                "Industry-leading noise cancellation with 30-hour battery",
                                new BigDecimal("399.99"),
                                50
                        )
                );

                for (Product product : initialProducts) {
                    productRepositoryPort.save(product);
                    log.info("Seeded product: '{}' (SKU: {}, Stock: {})",
                            product.getName(), product.getSku(), product.getAvailableQuantity());
                }

                log.info("Catalog seeding completed successfully. {} products loaded.", initialProducts.size());

            } else {
                log.info("Inventory already contains products. Skipping seeding.");
            }
        };

    }



}

