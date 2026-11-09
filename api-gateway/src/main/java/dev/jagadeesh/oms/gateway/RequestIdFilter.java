package dev.jagadeesh.oms.gateway;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adds X-Request-Id (or keeps the caller's) so one request can be followed through every service's logs.
 * A WebFilter ahead of Spring Security, so even a 401 from the gateway carries the id.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter implements WebFilter {

    static final String HEADER = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String id = exchange.getRequest().getHeaders().getFirst(HEADER);
        String requestId = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        ServerWebExchange withId = exchange.mutate().request(r -> r.header(HEADER, requestId)).build();
        withId.getResponse().getHeaders().set(HEADER, requestId);
        return chain.filter(withId);
    }
}
