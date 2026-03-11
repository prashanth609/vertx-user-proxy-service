package com.example.userproxy.http;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;

public record ProxyResponse(
        int statusCode,
        Buffer body,
        MultiMap headers
) {

    public static ProxyResponse serviceUnavailable(String message) {
        String json = """
                {
                  "message": "%s",
                  "status": 503
                }
                """.formatted(escapeJson(message));

        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.set("content-type", "application/json");
        return new ProxyResponse(503, Buffer.buffer(json), headers);
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
