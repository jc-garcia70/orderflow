package com.orderflow.inventory.application.port.in;

import com.orderflow.inventory.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

/**
 * Inbound port defining product catalog and warehouse management operations.
 */
public interface ProductUseCase {

    record CreateProductCommand(
            String sku,
            String name,
            String description,
            BigDecimal price,
            Integer initialQuantity
    ){}


    record UpdateProductCommand(
            String id,
            String name,
            String description,
            BigDecimal price
    ){}

    Product createProduct(CreateProductCommand command);

    Product updateProduct(UpdateProductCommand command);

    Product restockProduct(String id, int quantity);

    Product getProductById(String id);

    Product getProductBySku(String sku);

    Page<Product> getAllProducts(Pageable pageable);

    void activateProduct(String id);

    void deactivateProduct(String id);


}
