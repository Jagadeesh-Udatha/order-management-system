package dev.jagadeesh.oms.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTest {

    @Autowired RouteLocator routes;
    @Autowired WebTestClient web;

    @Test
    void everyServiceHasARoute() {
        assertThat(routes.getRoutes().map(Route::getId).collectList().block())
                .containsExactlyInAnyOrder("auth", "catalog", "orders", "payments", "notifications");
    }

    @Test
    void ordersWithoutATokenStopAtTheGateway() {
        web.get().uri("/api/orders").exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().exists("X-Request-Id");
    }

    @Test
    void aForgedTokenIsRejected() {
        web.get().uri("/api/orders").header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.bad")
                .exchange().expectStatus().isUnauthorized();
    }
}
