package com.orderflow.inventory.application.port.out;

import com.orderflow.inventory.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Outbound port defining persistence operations for the product aggregate.
 */
public interface ProductRepositoryPort {

    Product save(Product product);

    Optional<Product> findById(String id);

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Page<Product> findAll(Pageable pageable);

}


