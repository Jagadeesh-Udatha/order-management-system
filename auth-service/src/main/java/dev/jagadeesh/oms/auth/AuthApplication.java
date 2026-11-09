package dev.jagadeesh.oms.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"dev.jagadeesh.oms.auth", "dev.jagadeesh.oms.common.web", "dev.jagadeesh.oms.common.security"})
@ConfigurationPropertiesScan
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
