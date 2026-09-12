package com.javalb.loadbalancer.core;

/** Simulated client affinity: pins a clientId to a backend id. */
public final class StickySessionManager {

    private final java.util.Map<String, String> pinned = new java.util.concurrent.ConcurrentHashMap<>();

    public String pinnedNode(String clientId) {
        return pinned.get(clientId);
    }

    public void pin(String clientId, String nodeId) {
        pinned.put(clientId, nodeId);
    }

    public void unpin(String clientId) {
        pinned.remove(clientId);
    }

    public void clearForNode(String nodeId) {
        pinned.entrySet().removeIf(e -> e.getValue().equals(nodeId));
    }

    public void clearAll() {
        pinned.clear();
    }

    public int size() {
        return pinned.size();
    }
}