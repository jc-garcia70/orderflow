package com.orderflow.orders.infrastructure.adapter.out.catalog;

import com.orderflow.common.dto.ApiResponse;
import com.orderflow.common.exception.ResourceNotFoundException;
import com.orderflow.orders.application.exception.InventoryServiceUnavailableException;
import com.orderflow.orders.application.port.out.ProductCatalogPort;
import com.orderflow.orders.infrastructure.adapter.out.catalog.dto.InventoryProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class InventoryProductRestAdapter implements ProductCatalogPort {

    private static final ParameterizedTypeReference<ApiResponse<InventoryProductResponse>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public InventoryProductRestAdapter(
            @Value("${inventory-service.base-url}") String baseUrl,
            @Value("${inventory-service.connect-timeout:2s}") Duration connectTimeout,
            @Value("${inventory-service.read-timeout:3s}") Duration readTimeout
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public ProductDetails getById(String productId) {
        try {
            ApiResponse<InventoryProductResponse> response = restClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .retrieve()
                    .body(RESPONSE_TYPE);

            if (response == null || response.data() == null) {
                throw new InventoryServiceUnavailableException(
                        "Inventory service returned an empty product response",
                        new IllegalStateException("Product response data was missing")
                );
            }

            InventoryProductResponse product = response.data();
            return new ProductDetails(product.price(), product.active());

        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResourceNotFoundException("Product", productId);
        } catch (RestClientException ex) {
            throw new InventoryServiceUnavailableException(
                    "Inventory service is unavailable",
                    ex
            );
        }
    }
}