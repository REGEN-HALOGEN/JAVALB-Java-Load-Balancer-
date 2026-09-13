package com.javalb.loadbalancer;

import com.javalb.loadbalancer.api.WsHub;
import com.javalb.loadbalancer.core.LoadBalancerEngine;
import com.javalb.loadbalancer.core.RequestContext;
import com.javalb.loadbalancer.persistence.SupabaseRepo;
import com.javalb.loadbalancer.traffic.LoadGenerator;
import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * JavaLB application entry point.
 *
 * <p>Serves an interactive control plane (REST) plus a live statistics stream
 * (Server-Sent Events) consumed by the Vercel-hosted Next.js dashboard.
 */
public final class App {

    /** Seed topology: three backends with different weights/latencies so strategy differences are visible. */
    record BackendSpec(String id, int weight, long latency, double sigma, double error, int capacity) {
    }

    static final BackendSpec[] DEFAULT_BACKENDS = {
            new BackendSpec("node-a", 3, 20, 4, 0.5, 25),
            new BackendSpec("node-b", 2, 45, 12, 2.0, 15),
            new BackendSpec("node-c", 1, 80, 20, 5.0, 10)
    };

    private App() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        String corsOrigins = System.getenv("CORS_ORIGINS");

        LoadBalancerEngine engine = new LoadBalancerEngine();
        for (BackendSpec spec : DEFAULT_BACKENDS) {
            engine.addNode(spec.id(), spec.weight(), spec.latency(), spec.sigma(), spec.error(), spec.capacity());
        }

        LoadGenerator generator = new LoadGenerator(engine);
        SupabaseRepo repo = new SupabaseRepo();
        WsHub hub = WsHub.create();
        hub.setSnapshotProvider(() -> fullSnapshot(engine, generator));
        engine.setEventSink(hub::broadcast);

        // While an experiment is running, sample time-series points every 2s.
        String[] activeExperiment = {null};
        ScheduledExecutorService pointSampler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("experiment-sampler-").factory());
        pointSampler.scheduleWithFixedDelay(() -> {
            if (activeExperiment[0] != null && repo.isAvailable()) {
                repo.recordPoint(activeExperiment[0], fullSnapshot(engine, generator));
            }
        }, 2, 2, TimeUnit.SECONDS);

        Javalin app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.http.defaultContentType = "application/json";
        });

        // Manual CORS headers (version-stable; grants origins from CORS_ORIGINS, or any if unset).
        app.before(ctx -> {
            String origin = ctx.header("Origin");
            if (origin != null && originAllowed(origin, corsOrigins)) {
                ctx.header("Access-Control-Allow-Origin", origin);
                ctx.header("Vary", "Origin");
            }
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, x-client-id");
        });

        app.options("/*", ctx -> ctx.status(204));

        // ---- live stream (WebSocket) ----
        app.ws("/ws/events", ws -> {
            ws.onConnect(ctx -> hub.onConnect(ctx.session));
            ws.onClose(ctx -> hub.onDisconnect(ctx.session));
            ws.onError(ctx -> hub.onDisconnect(ctx.session));
        });

        // ---- health ----
        app.get("/health", ctx -> ctx.result("ok"));

        // ---- state ----
        app.get("/api/state", ctx -> ctx.json(fullSnapshot(engine, generator)));

        // ---- config: strategy, sticky sessions, health interval, backend mutations ----
        app.post("/api/config", ctx -> {
            @SuppressWarnings("unchecked") Map<String, Object> body = ctx.bodyAsClass(Map.class);
            handleConfig(engine, body);
            ctx.json(fullSnapshot(engine, generator));
        });

        // ---- load generator ----
        app.post("/api/load", ctx -> {
            @SuppressWarnings("unchecked") Map<String, Object> body = ctx.bodyAsClass(Map.class);
            boolean running = body.get("running") == null
                    ? Boolean.TRUE.equals(generator.status().get("running"))
                    : Boolean.parseBoolean(String.valueOf(body.get("running")));
            double rate = toDouble(body.get("ratePerSec"), 60);
            int concurrency = toInt(body.get("concurrency"), 24);
            String pattern = asString(body.get("pattern"), "steady");
            int numClients = toInt(body.get("numClients"), 12);
            long durationSec = toLong(body.get("durationSec"), 0);
            generator.configure(running, rate, concurrency, pattern, numClients, durationSec);
            ctx.json(fullSnapshot(engine, generator));
        });

        // ---- one manual request through the balancer ----
        app.post("/api/proxy", ctx -> {
            @SuppressWarnings("unchecked") Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String client = asString(body.get("clientId"), "manual-client");
            String path = asString(body.get("path"), "/req");
            ctx.json(engine.route(RequestContext.of(client, path)));
        });

        // ---- experiments (recorded to Supabase) ----
        app.post("/api/experiment", ctx -> {
            @SuppressWarnings("unchecked") Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String name = asString(body.get("name"), "experiment");
            if (activeExperiment[0] == null) {
                engine.resetStats();
                activeExperiment[0] = repo.beginExperiment(name, engine.strategyName(), Map.copyOf(fullSnapshot(engine, generator)));
            }
            ctx.json(Map.of("experimentId", activeExperiment[0], "started", activeExperiment[0] != null));
        });

        app.post("/api/experiment/stop", ctx -> {
            String expId = activeExperiment[0];
            activeExperiment[0] = null;
            Map<String, Object> result = repo.finishExperiment(expId, fullSnapshot(engine, generator));
            ctx.json(result);
        });

        app.get("/api/experiments", ctx -> ctx.json(repo.listExperiments(50)));

        app.post("/api/reset-stats", ctx -> {
            engine.resetStats();
            ctx.json(Map.of("ok", true));
        });

        app.start(port);
        System.out.println("[javalb] listening on http://0.0.0.0:" + port);
        System.out.println("[javalb] live stream:   ws://<host>:" + port + "/ws/events");
        System.out.println("[javalb] control plane: /api/state /api/config /api/load /api/proxy /api/experiment*");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.stop();
            pointSampler.shutdownNow();
            repo.close();
        }));
    }

    private static Map<String, Object> fullSnapshot(LoadBalancerEngine engine, LoadGenerator generator) {
        Map<String, Object> snap = engine.snapshot();
        snap.put("load", generator.status());
        return snap;
    }

    private static void handleConfig(LoadBalancerEngine engine, Map<String, Object> body) {
        Object strategy = body.get("strategy");
        if (strategy != null) {
            engine.setStrategy(String.valueOf(strategy));
        }
        Object sticky = body.get("stickyEnabled");
        if (sticky != null) {
            engine.setStickyEnabled(Boolean.parseBoolean(String.valueOf(sticky)));
        }
        Object healthInterval = body.get("healthIntervalMs");
        if (healthInterval != null) {
            engine.healthChecker().setIntervalMs(toLong(healthInterval, 2000));
        }
        Object backends = body.get("backends");
        if (backends instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> raw)) {
                    continue;
                }
                Map<String, Object> spec = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : raw.entrySet()) {
                    spec.put(String.valueOf(e.getKey()), e.getValue());
                }
                applyBackendOp(engine, spec);
            }
        }
    }

    private static void applyBackendOp(LoadBalancerEngine engine, Map<String, Object> spec) {
        String action = asString(spec.get("action"), "update");
        String id = asString(spec.get("id"), null);
        switch (action) {
            case "add" -> {
                try {
                    engine.addNode(id,
                            toInt(spec.get("weight"), 1),
                            toLong(spec.get("baseLatencyMs"), 20),
                            toDouble(spec.get("sigmaLatencyMs"), 5),
                            toDouble(spec.get("errorRatePct"), 1),
                            toInt(spec.get("capacity"), 20));
                } catch (IllegalStateException e) {
                    System.err.println("[javalb] add node skipped: " + e.getMessage());
                }
            }
            case "remove" -> engine.removeNode(id);
            default -> {
                var node = engine.find(id);
                if (node == null) {
                    return;
                }
                if (spec.containsKey("weight")) {
                    node.setWeight(toInt(spec.get("weight"), node.weight()));
                }
                if (spec.containsKey("baseLatencyMs")) {
                    node.setBaseLatencyMs(toLong(spec.get("baseLatencyMs"), node.baseLatencyMs()));
                }
                if (spec.containsKey("sigmaLatencyMs")) {
                    node.setSigmaLatencyMs(toDouble(spec.get("sigmaLatencyMs"), node.sigmaLatencyMs()));
                }
                if (spec.containsKey("errorRatePct")) {
                    node.setErrorRatePct(toDouble(spec.get("errorRatePct"), node.errorRatePct()));
                }
                if (spec.containsKey("capacity")) {
                    node.setCapacity(toInt(spec.get("capacity"), node.capacity()));
                }
                switch (asString(spec.get("op"), "")) {
                    case "pause" -> node.pause();
                    case "resume" -> node.resume();
                    case "kill" -> node.kill();
                    case "revive" -> node.revive();
                    default -> { /* control only */ }
                }
            }
        }
    }

    private static boolean originAllowed(String origin, String corsOrigins) {
        if (corsOrigins == null || corsOrigins.isBlank()) {
            return true;
        }
        for (String o : corsOrigins.split(",")) {
            if (o.trim().equalsIgnoreCase(origin)) {
                return true;
            }
        }
        return false;
    }

    private static int toInt(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static long toLong(Object v, long def) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static double toDouble(Object v, double def) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    private static String asString(Object v, String def) {
        return v == null ? def : String.valueOf(v);
    }
}