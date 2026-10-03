package com.orderflow.inventory.application.service;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.inventory.application.port.in.ProductUseCase;
import com.orderflow.inventory.application.port.in.ProductUseCase.CreateProductCommand;
import com.orderflow.inventory.application.port.in.ProductUseCase.UpdateProductCommand;
import com.orderflow.inventory.application.port.out.ProductRepositoryPort;
import com.orderflow.inventory.domain.model.Product;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepositoryPort productRepositoryPort;

    @InjectMocks
    private ProductService productService;

    private Product createSampleProduct(String id, String sku, int quantity) {
        return new Product(
                id,
                sku,
                "Test Product",
                "Description",
                new BigDecimal("99.99"),
                quantity,
                0,
                true,
                1L,
                null,
                null
        );
    }

    @Nested
    @DisplayName("Product Creation Tests")
    class CreateProductTests {

        @Test
        @DisplayName("Should successfully create product when SKU is unique")
        void shouldCreateProductSuccessfully() {
            CreateProductCommand command = new CreateProductCommand(
                    "SKU-001", "Monitor", "4K Monitor", new BigDecimal("350.00"), 10
            );
            when(productRepositoryPort.existsBySku("SKU-001")).thenReturn(false);
            when(productRepositoryPort.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
            Product result = productService.createProduct(command);
            assertThat(result).isNotNull();
            assertThat(result.getSku()).isEqualTo("SKU-001");
            assertThat(result.getName()).isEqualTo("Monitor");
            assertThat(result.getAvailableQuantity()).isEqualTo(10);
            verify(productRepositoryPort).save(any(Product.class));
        }

        @Test
        @DisplayName("Should throw BusinessException when SKU already exists")
        void shouldThrowBusinessExceptionWhenSkuAlreadyExists() {
            CreateProductCommand command = new CreateProductCommand(
                    "SKU-DUPLICATE", "Monitor", "4K Monitor", new BigDecimal("350.00"), 10
            );
            when(productRepositoryPort.existsBySku("SKU-DUPLICATE")).thenReturn(true);
            assertThatThrownBy(() -> productService.createProduct(command))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already exists");
            verify(productRepositoryPort, never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("Product Update and Restock Tests")
    class UpdateAndRestockTests {

        @Test
        @DisplayName("Should successfully update product details")
        void shouldUpdateProductDetails() {
            Product existing = createSampleProduct("prod-1", "SKU-001", 10);
            when(productRepositoryPort.findById("prod-1")).thenReturn(Optional.of(existing));
            when(productRepositoryPort.save(existing)).thenReturn(existing);
            UpdateProductCommand command = new UpdateProductCommand("prod-1", "New Name", "New Desc", new BigDecimal("120.00"));
            Product updated = productService.updateProduct(command);
            assertThat(updated.getName()).isEqualTo("New Name");
            assertThat(updated.getDescription()).isEqualTo("New Desc");
            assertThat(updated.getPrice()).isEqualByComparingTo(new BigDecimal("120.00"));
            verify(productRepositoryPort).save(existing);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when updating non-existent product")
        void shouldThrowWhenUpdatingNonExistentProduct() {
            when(productRepositoryPort.findById("prod-99")).thenReturn(Optional.empty());
            UpdateProductCommand command = new UpdateProductCommand("prod-99", "Name", "Desc", new BigDecimal("10.00"));
            assertThatThrownBy(() -> productService.updateProduct(command))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should successfully restock product")
        void shouldRestockProduct() {
            Product existing = createSampleProduct("prod-1", "SKU-001", 10);
            when(productRepositoryPort.findById("prod-1")).thenReturn(Optional.of(existing));
            when(productRepositoryPort.save(existing)).thenReturn(existing);
            Product restocked = productService.restockProduct("prod-1", 15);
            assertThat(restocked.getAvailableQuantity()).isEqualTo(25);
            verify(productRepositoryPort).save(existing);
        }
    }

    @Nested
    @DisplayName("Product Status and Query Tests")
    class StatusAndQueryTests {

        @Test
        @DisplayName("Should activate and deactivate product")
        void shouldToggleActivation() {
            Product product = createSampleProduct("prod-1", "SKU-001", 5);
            when(productRepositoryPort.findById("prod-1")).thenReturn(Optional.of(product));
            when(productRepositoryPort.save(product)).thenReturn(product);
            productService.deactivateProduct("prod-1");
            assertThat(product.isActive()).isFalse();
            productService.activateProduct("prod-1");
            assertThat(product.isActive()).isTrue();
        }

        @Test
        @DisplayName("Should retrieve paginated products")
        void shouldGetAllProductsPaged() {
            Pageable pageable = PageRequest.of(0, 10);
            List<Product> list = List.of(createSampleProduct("p1", "SKU-1", 5));
            Page<Product> expectedPage = new PageImpl<>(list, pageable, 1);
            when(productRepositoryPort.findAll(pageable)).thenReturn(expectedPage);
            Page<Product> result = productService.getAllProducts(pageable);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }


    }

}
