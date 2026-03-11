package com.example.userproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "proxy.server")
public record ProxyServerProperties(
        String host,
        int port,
        String prefix
) {
}
