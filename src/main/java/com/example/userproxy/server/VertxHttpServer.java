package com.example.userproxy.server;

import com.example.userproxy.config.ProxyServerProperties;
import com.example.userproxy.http.PathMapper;
import com.example.userproxy.http.ProxyResponse;
import com.example.userproxy.service.UserServiceProxyClient;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VertxHttpServer {

    private final Vertx vertx;
    private final ProxyServerProperties proxyServerProperties;
    private final UserServiceProxyClient proxyClient;

    private HttpServer httpServer;

    @PostConstruct
    public void start() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        router.get("/healthz").handler(ctx -> ctx.response()
                .putHeader("content-type", "application/json")
                .end("""
                        {"status":"UP"}
                        """));

        router.route(proxyServerProperties.prefix()).handler(this::handleProxy);
        router.route(proxyServerProperties.prefix() + "/*").handler(this::handleProxy);

        this.httpServer = vertx.createHttpServer()
                .requestHandler(router);

        this.httpServer.listen(proxyServerProperties.port(), proxyServerProperties.host())
                .onSuccess(server -> log.info("Vert.x proxy server started on {}:{}",
                        proxyServerProperties.host(),
                        proxyServerProperties.port()))
                .onFailure(error -> {
                    throw new IllegalStateException("Failed to start Vert.x proxy server", error);
                });
    }

    private void handleProxy(RoutingContext ctx) {
        HttpMethod method = ctx.request().method();
        String upstreamPath = PathMapper.toUserServicePath(ctx.normalizedPath(), proxyServerProperties.prefix());

        MultiMap queryParams = MultiMap.caseInsensitiveMultiMap();
        queryParams.addAll(ctx.queryParams());

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.addAll(ctx.request().headers());

        Buffer requestBody = ctx.body() == null ? Buffer.buffer() : ctx.body().buffer();

        proxyClient.forward(method, upstreamPath, queryParams, headers, requestBody)
                .onSuccess(proxyResponse -> writeResponse(ctx, proxyResponse))
                .onFailure(error -> {
                    log.error("Unexpected proxy failure for {} {}",
                            method,
                            ctx.normalizedPath(),
                            error);

                    ctx.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json")
                            .end("""
                                    {"message":"internal proxy error","status":500}
                                    """);
                });
    }

    private void writeResponse(RoutingContext ctx, ProxyResponse proxyResponse) {
        proxyResponse.headers().forEach(entry -> ctx.response().putHeader(entry.getKey(), entry.getValue()));
        ctx.response().setStatusCode(proxyResponse.statusCode()).end(proxyResponse.body());
    }

    @PreDestroy
    public void stop() {
        if (httpServer != null) {
            httpServer.close().onFailure(error -> log.warn("Error while closing Vert.x server", error));
        }
    }
}
