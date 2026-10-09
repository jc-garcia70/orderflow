package com.orderflow.users.infrastructure.config;

import com.orderflow.users.application.port.out.PasswordEncoderPort;
import com.orderflow.users.application.port.out.UserRepositoryPort;
import com.orderflow.users.domain.model.Role;
import com.orderflow.users.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds initial administrative account if the database is empty.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initAdminUser(
            UserRepositoryPort userRepositoryPort,
            PasswordEncoderPort passwordEncoderPort,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.admin.password:}") String adminPassword) {

        return args -> {
            if (adminEmail.isBlank() || adminPassword.isBlank()) {
                log.info("Admin seed skipped: app.admin.email / app.admin.password not configured");
                return;
            }
            if (!userRepositoryPort.existsByEmail(adminEmail)) {
                userRepositoryPort.save(new User(
                        adminEmail,
                        passwordEncoderPort.encode(adminPassword),
                        "System Administrator",
                        Role.ROLE_ADMIN));
                log.info("Initial admin created: {}", adminEmail);
            }
        };
    }
}
