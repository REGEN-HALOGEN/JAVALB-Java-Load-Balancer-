package com.javalb.loadbalancer.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

/**
 * An in-process mock HTTP backend. Behavior (latency, error rate, capacity) is
 * read live from the owning {@link BackendNode} at request time, so admin
 * changes apply to new requests immediately. Exposes:
 *
 * <ul>
 *   <li>{@code GET /healthz} — liveness probe used by the health checker</li>
 *   <li>{@code GET /*}           — simulated work with configurable latency/errors</li>
 * </ul>
 */
public final class MockBackendServer {

    private final BackendNode node;
    private HttpServer server;
    private ExecutorService executor;
    private volatile Semaphore capacityGate;

    public MockBackendServer(BackendNode node) {
        this.node = node;
    }

    public synchronized void start() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        capacityGate = new Semaphore(Math.max(1, node.capacity()));
        int threads = Math.max(4, node.capacity() * 2 + 4);
        executor = Executors.newFixedThreadPool(threads,
                Thread.ofPlatform().name("mock-" + node.id() + "-").factory());
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    public synchronized void close() {
        if (server != null) {
            server.stop(0);
        }
        server = null;
        if (executor != null) {
            executor.shutdownNow();
        }
        executor = null;
    }

    public boolean isRunning() {
        return server != null;
    }

    public void setCapacity(int c) {
        capacityGate = new Semaphore(Math.max(1, c));
    }

    /** Base URL of this backend, e.g. http://127.0.0.1:41287/ — null if not started. */
    public String baseUrl() {
        if (server == null) {
            return null;
        }
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (ex.getRequestURI().getPath().startsWith("/healthz")) {
                respond(ex, 200, "{\"ok\":true,\"node\":\"" + node.id() + "\"}");
                return;
            }
            if (!capacityGate.tryAcquire()) {
                respond(ex, 503, "{\"ok\":false,\"reason\":\"capacity-exhausted\"}");
                return;
            }
            try {
                long latency = node.baseLatencyMs()
                        + (long) Math.max(0, ThreadLocalRandom.current().nextGaussian() * node.sigmaLatencyMs());
                if (latency > 0) {
                    Thread.sleep(latency);
                }
                boolean fail = ThreadLocalRandom.current().nextDouble(0, 100) < node.errorRatePct();
                if (fail) {
                    respond(ex, 503, "{\"ok\":false,\"reason\":\"simulated-error\"}");
                } else {
                    respond(ex, 200, "{\"ok\":true,\"node\":\"" + node.id() + "\",\"latencyMs\":" + latency + "}");
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                try {
                    respond(ex, 503, "{\"ok\":false,\"reason\":\"interrupted\"}");
                } catch (IOException ignored) {
                    // client already gone
                }
            } finally {
                capacityGate.release();
            }
        } catch (Exception e) {
            try {
                respond(ex, 500, "{\"ok\":false,\"reason\":\"internal\"}");
            } catch (IOException ignored) {
                // client already gone
            }
        }
    }

    private static void respond(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}