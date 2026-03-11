package com.example.userproxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class VertxUserProxyApplication {

    public static void main(String[] args) {
        SpringApplication.run(VertxUserProxyApplication.class, args);
    }
}
