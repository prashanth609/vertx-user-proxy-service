package com.example.userproxy.http;

public final class PathMapper {

    private PathMapper() {
    }

    public static String toUserServicePath(String incomingPath, String proxyPrefix) {
        if (incomingPath == null || incomingPath.isBlank()) {
            return "/api/v1/users";
        }

        if (proxyPrefix == null || proxyPrefix.isBlank()) {
            return "/api/v1/users";
        }

        if (!incomingPath.startsWith(proxyPrefix)) {
            throw new IllegalArgumentException("Unexpected proxy path: " + incomingPath);
        }

        String suffix = incomingPath.substring(proxyPrefix.length());
        if (suffix.isBlank()) {
            return "/api/v1/users";
        }

        return "/api/v1/users" + suffix;
    }
}
