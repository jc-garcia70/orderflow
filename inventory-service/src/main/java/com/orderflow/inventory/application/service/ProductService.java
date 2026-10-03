package com.orderflow.inventory.application.service;

import com.orderflow.common.exception.BusinessException;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.inventory.application.port.in.ProductUseCase;
import com.orderflow.inventory.application.port.out.ProductRepositoryPort;
import com.orderflow.inventory.domain.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing product catalog and inventory management use cases.
 */
@Service
public class ProductService implements ProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;


    public ProductService(ProductRepositoryPort productRepositoryPort) {
        this.productRepositoryPort = productRepositoryPort;
    }


    @Override
    @Transactional
    public Product createProduct(CreateProductCommand command) {

        if(productRepositoryPort.existsBySku(command.sku())){
            throw new BusinessException(String.format("Product with SKU '%s' already exists", command.sku()));
        }

        Product product = new Product(
                command.sku(),
                command.name(),
                command.description(),
                command.price(),
                command.initialQuantity()
        );

        return productRepositoryPort.save(product);
    }


    @Override
    @Transactional
    public Product updateProduct(UpdateProductCommand command) {
        Product product = getProductById(command.id());
        product.updateDetails(command.name(),command.description(),command.price());
        return productRepositoryPort.save(product);
    }


    @Override
    @Transactional
    public Product restockProduct(String id, int quantity) {
        Product product = getProductById(id);
        product.restock(quantity);
        return productRepositoryPort.save(product);
    }


    @Override
    @Transactional(readOnly = true)
    public Product getProductById(String id) {
        return productRepositoryPort.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("Product", id));
    }


    @Override
    @Transactional(readOnly = true)
    public Product getProductBySku(String sku) {
        return productRepositoryPort.findBySku(sku)
                .orElseThrow(()-> new ResourceNotFoundException("Product with SKU",sku));
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepositoryPort.findAll(pageable);
    }


    @Override
    @Transactional
    public void activateProduct(String id) {
        Product product = getProductById(id);
        product.activate();
        productRepositoryPort.save(product);
    }


    @Override
    @Transactional
    public void deactivateProduct(String id) {
        Product product = getProductById(id);
        product.deactivate();
        productRepositoryPort.save(product);
    }


}
