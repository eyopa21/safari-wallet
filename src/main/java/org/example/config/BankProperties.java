package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "bank")
public record BankProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration pollInterval,
        Duration retryInterval,
        int batchSize) {
}
