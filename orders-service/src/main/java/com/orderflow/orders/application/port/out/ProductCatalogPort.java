package com.orderflow.orders.application.port.out;

import java.math.BigDecimal;

public interface ProductCatalogPort {

    record ProductDetails(BigDecimal price, boolean active){}

    ProductDetails getById(String productId);

}
