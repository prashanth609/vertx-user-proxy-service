package com.example.userproxy.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathMapperTest {

    @Test
    void shouldMapCollectionEndpoint() {
        assertEquals("/api/v1/users",
                PathMapper.toUserServicePath("/api/v1/user-proxy", "/api/v1/user-proxy"));
    }

    @Test
    void shouldMapResourceEndpoint() {
        assertEquals("/api/v1/users/42",
                PathMapper.toUserServicePath("/api/v1/user-proxy/42", "/api/v1/user-proxy"));
    }

    @Test
    void shouldMapSearchEndpoint() {
        assertEquals("/api/v1/users/search",
                PathMapper.toUserServicePath("/api/v1/user-proxy/search", "/api/v1/user-proxy"));
    }

    @Test
    void shouldRejectUnexpectedPath() {
        assertThrows(IllegalArgumentException.class,
                () -> PathMapper.toUserServicePath("/wrong-prefix/42", "/api/v1/user-proxy"));
    }
}
