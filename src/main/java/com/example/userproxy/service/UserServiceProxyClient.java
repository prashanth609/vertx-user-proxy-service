package com.example.userproxy.service;

import com.example.userproxy.config.UserServiceProperties;
import com.example.userproxy.http.ProxyResponse;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.RetryPolicy;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

@Slf4j
@Component
public class UserServiceProxyClient {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    private final WebClient webClient;
    private final UserServiceProperties properties;
    private final CircuitBreaker circuitBreaker;
    private final URI baseUri;

    public UserServiceProxyClient(WebClient webClient, Vertx vertx, UserServiceProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
        this.baseUri = URI.create(trimTrailingSlash(properties.baseUrl()));

        UserServiceProperties.CircuitBreakerProperties cb = properties.circuitBreaker();
        this.circuitBreaker = CircuitBreaker.create(cb.name(), vertx,
                        new CircuitBreakerOptions()
                                .setMaxFailures(cb.maxFailures())
                                .setTimeout(cb.timeoutMs())
                                .setResetTimeout(cb.resetTimeoutMs())
                                .setFallbackOnFailure(true)
                                .setMaxRetries(cb.maxRetries()))
                .retryPolicy(RetryPolicy.exponentialDelayWithJitter(
                        cb.backoffInitialDelayMs(),
                        cb.backoffMaxDelayMs()));
    }

    public Future<ProxyResponse> forward(HttpMethod method,
                                         String upstreamPath,
                                         MultiMap queryParams,
                                         MultiMap incomingHeaders,
                                         Buffer requestBody) {

        return circuitBreaker.executeWithFallback(
                promise -> doForward(method, upstreamPath, queryParams, incomingHeaders, requestBody)
                        .onComplete(promise),
                throwable -> {
                    log.warn("Fallback triggered for {} {}: {}", method, upstreamPath, throwable.getMessage());
                    return ProxyResponse.serviceUnavailable(
                            "user-service is temporarily unavailable. Please retry shortly."
                    );
                }
        );
    }

    private Future<ProxyResponse> doForward(HttpMethod method,
                                            String upstreamPath,
                                            MultiMap queryParams,
                                            MultiMap incomingHeaders,
                                            Buffer requestBody) {

        int port = resolvePort();
        boolean ssl = "https".equalsIgnoreCase(baseUri.getScheme());
        String host = baseUri.getHost();
        String requestUri = upstreamPathWithBasePath(upstreamPath);

        HttpRequest<Buffer> request = webClient
                .request(method, port, host, requestUri)
                .ssl(ssl)
                .timeout(properties.requestTimeoutMs());

        if (queryParams != null) {
            queryParams.forEach(entry -> request.addQueryParam(entry.getKey(), entry.getValue()));
        }

        copyHeaders(incomingHeaders, request.headers());

        Future<HttpResponse<Buffer>> responseFuture = hasBody(requestBody) ? request.sendBuffer(requestBody) : request.send();

        return responseFuture.compose(response -> {
            if (response.statusCode() >= 500) {
                return Future.failedFuture("Upstream 5xx response: " + response.statusCode());
            }
            return Future.succeededFuture(toProxyResponse(response));
        });
    }

    private ProxyResponse toProxyResponse(HttpResponse<Buffer> response) {
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();

        response.headers().forEach(entry -> {
            String lower = entry.getKey().toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lower)) {
                headers.add(entry.getKey(), entry.getValue());
            }
        });

        if (!headers.contains("content-type")) {
            headers.set("content-type", "application/json");
        }

        Buffer body = response.body() != null ? response.body() : Buffer.buffer();
        return new ProxyResponse(response.statusCode(), body, headers);
    }

    private void copyHeaders(MultiMap source, MultiMap target) {
        if (source == null) {
            return;
        }

        source.forEach(entry -> {
            String lower = entry.getKey().toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lower)) {
                target.add(entry.getKey(), entry.getValue());
            }
        });
    }

    private boolean hasBody(Buffer requestBody) {
        return requestBody != null && requestBody.length() > 0;
    }

    private int resolvePort() {
        if (baseUri.getPort() != -1) {
            return baseUri.getPort();
        }
        return "https".equalsIgnoreCase(baseUri.getScheme()) ? 443 : 80;
    }

    private String upstreamPathWithBasePath(String upstreamPath) {
        String basePath = baseUri.getPath() == null ? "" : baseUri.getPath();
        if (basePath.endsWith("/")) {
            basePath = basePath.substring(0, basePath.length() - 1);
        }
        return basePath + upstreamPath;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("user-service.base-url must not be blank");
        }

        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
