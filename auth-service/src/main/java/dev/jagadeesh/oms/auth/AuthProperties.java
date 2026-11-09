package dev.jagadeesh.oms.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "oms.auth")
public record AuthProperties(Duration tokenTtl, String adminEmail, String adminPassword) {
}
