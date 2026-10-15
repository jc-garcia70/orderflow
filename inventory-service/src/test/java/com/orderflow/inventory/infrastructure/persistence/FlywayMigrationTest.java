package com.orderflow.inventory.infrastructure.persistence;

import com.orderflow.inventory.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "outbox.relay.enabled=false"
})
@ActiveProfiles("test")
class FlywayMigrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("V1 is applied successfully and the context starts with ddl-auto=validate")
    void migrationIsApplied() {
        List<String> applied = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE", String.class);

        assertThat(applied).contains("1");
    }

    @Test
    @DisplayName("All expected tables exist")
    void tablesExist() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "products", "processed_order_reservations", "stock_reservations",
                "stock_reservation_items", "inventory_outbox");
    }

    @Test
    @DisplayName("The database itself rejects negative stock")
    void databaseRejectsNegativeStock() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO products (id, sku, name, price, available_quantity, reserved_quantity,
                                      active, created_at, updated_at)
                VALUES ('p-neg', 'SKU-NEG', 'Bad', 1.00, -1, 0, TRUE, now(), now())
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}