package com.orderflow.users.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for UserJpaEntity
 */
@Repository
public interface SpringDataUserRepository extends JpaRepository<UserJpaEntity,String> {

    Optional<UserJpaEntity> findByEmail(String email);

    boolean existsByEmail(String email);

}
