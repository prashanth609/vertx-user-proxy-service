package com.example.userproxy.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VertxConfig {

    @Bean(destroyMethod = "close")
    public Vertx vertx() {
        return Vertx.vertx(new VertxOptions());
    }

    @Bean
    public WebClient webClient(Vertx vertx, UserServiceProperties userServiceProperties) {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout(userServiceProperties.connectTimeoutMs())
                .setKeepAlive(true)
                .setTryUseCompression(true)
                .setUserAgent("vertx-user-proxy-service/0.0.1");

        return WebClient.create(vertx, options);
    }
}
