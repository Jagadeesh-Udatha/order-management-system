package dev.jagadeesh.oms.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Synchronous read of the current price when the order is placed. The price is copied onto the order line, so a
 * later price change never alters an order that was already placed. Stock is NOT checked here; that is the
 * asynchronous part of the saga, because a read here could be stale by the time the order is saved.
 */
@Component
public class CatalogClient {

    public record ProductInfo(UUID id, String name, BigDecimal price, boolean active) {
    }

    private final RestClient http;

    public CatalogClient(RestClient.Builder builder, @Value("${oms.catalog.url:http://localhost:8082}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.http = builder.baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Optional<ProductInfo> product(UUID id) {
        return Optional.ofNullable(http.get().uri("/api/products/{id}", id)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { })
                .body(ProductInfo.class))
                .filter(p -> p.id() != null);
    }
}
