package dev.jagadeesh.oms.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// no JPA here, so only the web + security parts of common are scanned (not the outbox/inbox entities)
@SpringBootApplication(scanBasePackages = {"dev.jagadeesh.oms.notification", "dev.jagadeesh.oms.common.web", "dev.jagadeesh.oms.common.security"})
public class NotificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
