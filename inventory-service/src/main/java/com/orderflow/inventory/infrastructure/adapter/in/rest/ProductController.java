package com.orderflow.inventory.infrastructure.adapter.in.rest;

import com.orderflow.common.dto.ApiResponse;
import com.orderflow.inventory.application.port.in.ProductUseCase;
import com.orderflow.inventory.domain.model.Product;
import com.orderflow.inventory.infrastructure.adapter.in.rest.dto.CreateProductRequest;
import com.orderflow.inventory.infrastructure.adapter.in.rest.dto.ProductResponse;
import com.orderflow.inventory.infrastructure.adapter.in.rest.dto.RestockProductRequest;
import com.orderflow.inventory.infrastructure.adapter.in.rest.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductUseCase productUseCase;

    public ProductController(ProductUseCase productUseCase) {
        this.productUseCase = productUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> createProduct(@Valid
                         @RequestBody CreateProductRequest request){

        ProductUseCase.CreateProductCommand command = new ProductUseCase.CreateProductCommand(
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.initialQuantity()
        );

        Product product = productUseCase.createProduct(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", ProductResponse.fromDomain(product)));

    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProductBySku(@PathVariable String sku) {
        Product product = productUseCase.getProductBySku(sku);
        return ResponseEntity.ok(ApiResponse.success(ProductResponse.fromDomain(product)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getAllProducts(@PageableDefault(size = 20) Pageable pageable) {
        Page<ProductResponse> products = productUseCase.getAllProducts(pageable).map(ProductResponse::fromDomain);
        return ResponseEntity.ok(ApiResponse.success(products));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody UpdateProductRequest request) {
        ProductUseCase.UpdateProductCommand command = new ProductUseCase.UpdateProductCommand(
                id,
                request.name(),
                request.description(),
                request.price()
        );
        Product updatedProduct = productUseCase.updateProduct(command);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", ProductResponse.fromDomain(updatedProduct)));
    }

    @PatchMapping("/{id}/restock")
    public ResponseEntity<ApiResponse<ProductResponse>> restockProduct(
            @PathVariable String id,
            @Valid @RequestBody RestockProductRequest request) {
        Product product = productUseCase.restockProduct(id, request.quantity());
        return ResponseEntity.ok(ApiResponse.success("Product restocked successfully", ProductResponse.fromDomain(product)));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateProduct(@PathVariable String id) {
        productUseCase.activateProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product activated successfully", null));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateProduct(@PathVariable String id) {
        productUseCase.deactivateProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deactivated successfully", null));
    }

}
