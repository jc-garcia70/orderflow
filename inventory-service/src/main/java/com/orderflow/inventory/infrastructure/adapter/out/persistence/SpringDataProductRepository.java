package com.orderflow.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for product database operations.
 */
@Repository
public interface SpringDataProductRepository extends JpaRepository<ProductJpaEntity, String> {


    Optional<ProductJpaEntity> findBySku(String sku);

    boolean existsBySku(String sku);

}
