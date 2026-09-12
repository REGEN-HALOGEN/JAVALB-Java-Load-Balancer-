package com.javalb.loadbalancer.core;

/**
 * A single unit of work flowing through the load balancer.
 *
 * @param clientId the (simulated) client making the request — used by sticky sessions and IP hashing
 * @param path     the requested path (defaults to /req)
 */
public record RequestContext(String clientId, String path) {

    public static RequestContext of(String clientId) {
        return new RequestContext(normalizeClient(clientId), "/req");
    }

    public static RequestContext of(String clientId, String path) {
        return new RequestContext(normalizeClient(clientId), path == null || path.isBlank() ? "/req" : path);
    }

    private static String normalizeClient(String clientId) {
        return clientId == null || clientId.isBlank() ? "unknown" : clientId;
    }
}