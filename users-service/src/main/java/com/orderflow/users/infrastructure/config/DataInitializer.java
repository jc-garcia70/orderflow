package com.orderflow.users.infrastructure.config;

import com.orderflow.users.application.port.out.PasswordEncoderPort;
import com.orderflow.users.application.port.out.UserRepositoryPort;
import com.orderflow.users.domain.model.Role;
import com.orderflow.users.domain.model.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Seeds initial administrative account if the database is empty.
 */
@Configuration
public class DataInitializer {


    @Bean
    public CommandLineRunner initAdminUser(UserRepositoryPort userRepositoryPort,
                                           PasswordEncoderPort passwordEncoderPort){

        return args -> {
            String adminEmail = "admin@gmial.com";
            if (!userRepositoryPort.existsByEmail(adminEmail)) {
                User admin = new User(
                        adminEmail,
                        passwordEncoderPort.encode("Admin123!"),
                        "System Administrator",
                        Role.ROLE_ADMIN
                );
                userRepositoryPort.save(admin);
                System.out.println("Initial admin created: " + adminEmail + " / Admin123!");
            }
        };

    }

}
