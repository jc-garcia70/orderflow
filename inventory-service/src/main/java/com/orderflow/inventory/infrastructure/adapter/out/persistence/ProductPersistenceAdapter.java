package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import com.orderflow.common.exception.ConcurrencyConflictException;
import com.orderflow.inventory.application.port.out.ProductRepositoryPort;
import com.orderflow.inventory.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Secondary adapter implementing ProductRepositoryPort using Spring Data JPA.
 * Bridges domain models with relations persistence and handles optimistic locking conflicts.
 */
@Component
public class ProductPersistenceAdapter implements ProductRepositoryPort {

    private final SpringDataProductRepository repository;

    public ProductPersistenceAdapter(SpringDataProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public Product save(Product product) {
        String id = product.getId() != null ? product.getId() : UUID.randomUUID().toString();
        ProductJpaEntity entity = new ProductJpaEntity(
                id,
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getAvailableQuantity(),
                product.getReservedQuantity(),
                product.isActive(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
        try {
            ProductJpaEntity savedEntity = repository.save(entity);
            return toDomain(savedEntity);
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConcurrencyConflictException(
                    String.format("Optimistic locking conflict on product '%s' (SKU: %s). Concurrent update detected.",
                            id, product.getSku()),
                    ex
            );
        }
    }

    @Override
    public Optional<Product> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Product> findBySku(String sku) {
        return repository.findBySku(sku).map(this::toDomain);
    }

    @Override
    public boolean existsBySku(String sku) {
        return repository.existsBySku(sku);
    }

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toDomain);
    }

    private Product toDomain(ProductJpaEntity entity) {
        return new Product(
                entity.getId(),
                entity.getSku(),
                entity.getName(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getAvailableQuantity(),
                entity.getReservedQuantity(),
                entity.isActive(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }


}
