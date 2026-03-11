package com.example.userproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user-service")
public record UserServiceProperties(
        String baseUrl,
        int requestTimeoutMs,
        int connectTimeoutMs,
        CircuitBreakerProperties circuitBreaker
) {

    public record CircuitBreakerProperties(
            String name,
            int maxFailures,
            long timeoutMs,
            long resetTimeoutMs,
            int maxRetries,
            long backoffInitialDelayMs,
            long backoffMaxDelayMs
    ) {
    }
}
