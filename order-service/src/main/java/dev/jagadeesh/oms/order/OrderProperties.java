package dev.jagadeesh.oms.order;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** pendingTimeout: an order still waiting on inventory or payment after this long is cancelled. */
@ConfigurationProperties(prefix = "oms.orders")
public record OrderProperties(Duration pendingTimeout) {
    public OrderProperties {
        if (pendingTimeout == null) {
            pendingTimeout = Duration.ofMinutes(15);
        }
    }
}
