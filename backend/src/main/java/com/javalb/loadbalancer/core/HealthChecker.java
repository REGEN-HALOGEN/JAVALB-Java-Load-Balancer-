package com.javalb.loadbalancer.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Active health probing: periodically issues {@code GET /healthz} on every
 * backend. Combined with the passive failure counters bumped by real traffic,
 * it drives {@code HEALTHY -> DOWN} and {@code DOWN -> HEALTHY} transitions.
 */
public final class HealthChecker {

    public static final int FAILURE_THRESHOLD = 3;
    public static final int SUCCESS_THRESHOLD = 2;

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(800))
            .build();

    private final List<BackendNode> nodes;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "health-checker");
                t.setDaemon(true);
                return t;
            });

    private volatile long intervalMs = 2000;
    private boolean started;

    public HealthChecker(List<BackendNode> nodes) {
        this.nodes = nodes;
    }

    public long intervalMs() { return intervalMs; }

    public void setIntervalMs(long ms) { this.intervalMs = Math.max(200, ms); }

    public synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        scheduler.submit(this::loop);
    }

    private void loop() {
        try {
            probeAll();
        } catch (Exception ignored) {
            // never let the health loop die
        } finally {
            // re-scheduled each pass so interval changes take effect live
            scheduler.schedule(this::loop, intervalMs, TimeUnit.MILLISECONDS);
        }
    }

    private void probeAll() {
        for (BackendNode node : nodes) {
            if (!node.server().isRunning()) {
                continue;
            }
            boolean ok = probe(node);
            node.onProbeResult(ok, FAILURE_THRESHOLD, SUCCESS_THRESHOLD);
        }
    }

    private static boolean probe(BackendNode node) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(node.server().baseUrl() + "healthz"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }
}