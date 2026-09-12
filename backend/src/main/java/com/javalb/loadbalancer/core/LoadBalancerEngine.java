package com.javalb.loadbalancer.core;

import com.javalb.loadbalancer.core.strategy.LoadBalancingStrategy;
import com.javalb.loadbalancer.core.strategy.Strategies;
import com.javalb.loadbalancer.metrics.MetricsStore;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Core routing engine. Picks a backend per the active strategy (plus optional
 * sticky sessions), forwards the request to the matching mock backend over real
 * HTTP, records statistics and publishes a per-request event.
 */
public final class LoadBalancerEngine {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(600))
            .build();

    private final CopyOnWriteArrayList<BackendNode> nodes = new CopyOnWriteArrayList<>();
    private final MetricsStore metrics = new MetricsStore();
    private final StickySessionManager sticky = new StickySessionManager();
    private final HealthChecker healthChecker = new HealthChecker(nodes);

    private volatile LoadBalancingStrategy strategy = Strategies.get("round-robin");
    private volatile boolean stickyEnabled = false;
    private volatile Consumer<Map<String, Object>> eventSink = m -> { };

    public LoadBalancerEngine() {
        healthChecker.start();
    }

    public List<BackendNode> nodes() { return nodes; }
    public MetricsStore metrics() { return metrics; }
    public HealthChecker healthChecker() { return healthChecker; }
    public String strategyName() { return strategy.name(); }
    public boolean stickyEnabled() { return stickyEnabled; }
    public StickySessionManager sticky() { return sticky; }

    public void setEventSink(Consumer<Map<String, Object>> sink) {
        this.eventSink = sink == null ? m -> { } : sink;
    }

    public String setStrategy(String name) {
        strategy = Strategies.get(name);
        return strategy.name();
    }

    public void setStickyEnabled(boolean on) {
        this.stickyEnabled = on;
        if (!on) {
            sticky.clearAll();
        }
    }

    public boolean containsNode(String id) {
        return find(id) != null;
    }

    /** Adds a backend. The in-process mock HTTP server starts immediately. */
    public synchronized BackendNode addNode(String id, int weight, long baseLatencyMs, double sigmaLatencyMs,
                                            double errorRatePct, int capacity) {
        String nodeId = id == null || id.isBlank() ? "node-" + (nodes.size() + 1) : id;
        if (containsNode(nodeId)) {
            throw new IllegalStateException("node already exists: " + nodeId);
        }
        BackendNode node = new BackendNode(nodeId, weight, baseLatencyMs, sigmaLatencyMs, errorRatePct, capacity);
        try {
            node.server().start();
        } catch (Exception e) {
            throw new IllegalStateException("failed to start mock backend: " + e.getMessage(), e);
        }
        nodes.add(node);
        return node;
    }

    /** Removes a backend and drops any sticky mappings pinned to it. */
    public synchronized boolean removeNode(String id) {
        BackendNode node = find(id);
        if (node == null) {
            return false;
        }
        node.server().close();
        nodes.remove(node);
        sticky.clearForNode(id);
        return true;
    }

    public BackendNode find(String id) {
        for (BackendNode n : nodes) {
            if (n.id().equals(id)) {
                return n;
            }
        }
        return null;
    }

    public List<BackendNode> eligibleNodes() {
        List<BackendNode> list = new ArrayList<>();
        for (BackendNode n : nodes) {
            if (n.isHealthy() && n.server().isRunning()) {
                list.add(n);
            }
        }
        return list;
    }

    /**
     * Routes one request end-to-end and returns a JSON-friendly result map
     * (also published to the live event stream).
     */
    public Map<String, Object> route(RequestContext ctx) {
        long startNs = System.nanoTime();
        List<BackendNode> eligible = eligibleNodes();

        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("type", "request");
        ev.put("id", UUID.randomUUID().toString().substring(0, 8));
        ev.put("ts", System.currentTimeMillis());
        ev.put("client", ctx.clientId());
        ev.put("path", ctx.path());
        ev.put("algorithm", strategy.name());

        if (eligible.isEmpty()) {
            ev.put("status", 503);
            ev.put("node", null);
            ev.put("latencyMs", 0);
            ev.put("reason", "no-healthy-backends");
            metrics.recordRequest(false, 0);
            publish(ev);
            return ev;
        }

        BackendNode selected = null;
        String reason = "";
        String pinnedNodeId = null;

        if (stickyEnabled) {
            pinnedNodeId = sticky.pinnedNode(ctx.clientId());
            if (pinnedNodeId != null) {
                BackendNode pinned = find(pinnedNodeId);
                if (pinned != null && pinned.isHealthy()) {
                    selected = pinned;
                    reason = "sticky-session -> " + pinnedNodeId;
                } else {
                    sticky.unpin(ctx.clientId());
                    pinnedNodeId = null;
                }
            }
        }

        if (selected == null) {
            selected = strategy.select(eligible, ctx);
            if (selected == null) {
                ev.put("status", 503);
                ev.put("node", null);
                ev.put("latencyMs", 0);
                ev.put("reason", "strategy-returned-none");
                metrics.recordRequest(false, 0);
                publish(ev);
                return ev;
            }
            reason = describeDecision(selected);
            if (stickyEnabled) {
                sticky.pin(ctx.clientId(), selected.id());
            }
        }

        selected.enter();
        int status;
        long latencyMs;
        boolean success;
        try {
            status = dispatch(selected, ctx);
            latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            success = status >= 200 && status < 400;
        } catch (Exception e) {
            status = 503;
            latencyMs = (System.nanoTime() - startNs) / 1_000_000;
            success = false;
        } finally {
            selected.leave();
        }

        long recordLatency = Math.max(1, latencyMs);
        int inFlightAfter = selected.inFlight();
        if (success) {
            selected.recordSuccess(recordLatency);
            metrics.recordRequest(true, recordLatency);
        } else {
            selected.recordFailure(recordLatency);
            metrics.recordRequest(false, recordLatency);
            selected.onTrafficFailure(HealthChecker.FAILURE_THRESHOLD);
            if (stickyEnabled && pinnedNodeId != null && selected.id().equals(pinnedNodeId)) {
                sticky.unpin(ctx.clientId());
            }
        }

        ev.put("status", status);
        ev.put("node", selected.id());
        ev.put("latencyMs", recordLatency);
        ev.put("inFlight", inFlightAfter);
        ev.put("reason", reason);
        publish(ev);
        return ev;
    }

    private int dispatch(BackendNode node, RequestContext ctx) throws Exception {
        String base = node.server().baseUrl();
        if (base == null) {
            return 503;
        }
        String path = ctx.path() == null ? "req" : ctx.path().replaceFirst("^/", "");
        URI uri = URI.create(base + path);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(4))
                .header("x-client-id", ctx.clientId())
                .GET()
                .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }

    private String describeDecision(BackendNode n) {
        return switch (strategy.name()) {
            case "least-connections" -> "least-connections -> " + n.id() + " (" + n.inFlight() + " in flight)";
            case "least-response-time" -> "least-response-time -> " + n.id() + " (" + Math.round(n.ewmaLatencyMs()) + "ms EWMA)";
            case "weighted-round-robin" -> "weighted-round-robin -> " + n.id() + " (weight " + n.weight() + ")";
            case "ip-hash" -> "ip-hash -> " + n.id();
            case "random" -> "random -> " + n.id();
            default -> "round-robin -> " + n.id();
        };
    }

    public void resetStats() {
        for (BackendNode n : nodes) {
            n.resetStats();
        }
        metrics.reset();
        sticky.clearAll();
    }

    private void publish(Map<String, Object> ev) {
        try {
            eventSink.accept(ev);
        } catch (Exception ignored) {
        }
    }

    /** JSON-friendly snapshot (without the live-stream "type" wrapper). */
    public Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ts", System.currentTimeMillis());
        out.put("algorithm", strategy.name());
        out.put("stickyEnabled", stickyEnabled);
        out.put("stickyMappings", sticky.size());
        out.put("healthIntervalMs", healthChecker.intervalMs());
        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (BackendNode n : nodes) {
            nodeList.add(n.snapshot());
        }
        out.put("nodes", nodeList);
        out.put("totals", metrics.snapshot());
        return out;
    }
}