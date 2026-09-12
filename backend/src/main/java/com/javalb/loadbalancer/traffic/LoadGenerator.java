package com.javalb.loadbalancer.traffic;

import com.javalb.loadbalancer.core.LoadBalancerEngine;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Traffic generator: fires requests through the engine at a configurable rate
 * and pattern, split across N virtual clients so sticky sessions and IP hashing
 * are visible in the topology view.
 */
public final class LoadGenerator {

    /** ~10 scheduler ticks per second; each tick dispatches a small batch. */
    private static final long TICK_MS = 100;

    private final LoadBalancerEngine engine;
    private final Object lock = new Object();

    private ScheduledExecutorService scheduler;
    private ExecutorService workers;
    private Semaphore concurrencyGate;

    private volatile boolean running;
    private volatile double ratePerSec = 60;
    private volatile int concurrency = 24;
    private volatile String pattern = "steady";
    private volatile int numClients = 12;
    private volatile long durationSec = 0; // 0 = unlimited
    private final AtomicLong clientCursor = new AtomicLong();
    private volatile long startedAt;

    public LoadGenerator(LoadBalancerEngine engine) {
        this.engine = engine;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", running);
        m.put("ratePerSec", ratePerSec);
        m.put("concurrency", concurrency);
        m.put("pattern", pattern);
        m.put("numClients", numClients);
        if (running) {
            m.put("startedAt", startedAt);
        }
        return m;
    }

    /** idempotent start/stop/configure. */
    public void configure(boolean run, double ratePerSec, int concurrency, String pattern,
                          int numClients, long durationSec) {
        synchronized (lock) {
            this.ratePerSec = Math.max(1, ratePerSec);
            this.concurrency = Math.max(1, concurrency);
            this.pattern = pattern == null || pattern.isBlank() ? "steady" : pattern;
            this.numClients = Math.max(1, numClients);
            this.durationSec = Math.max(0, durationSec);
            if (run && !this.running) {
                startLocked();
            } else if (!run && this.running) {
                stopLocked();
            }
        }
    }

    public void stop() {
        synchronized (lock) {
            if (running) {
                stopLocked();
            }
        }
    }

    private void startLocked() {
        stopLocked();
        running = true;
        startedAt = System.currentTimeMillis();
        concurrencyGate = new Semaphore(concurrency);
        workers = Executors.newFixedThreadPool(concurrency,
                Thread.ofPlatform().name("load-worker-").factory());
        scheduler = Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().name("load-tick-").factory());
        scheduler.schedule(this::tick, 0, TimeUnit.MILLISECONDS);
    }

    private void stopLocked() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (workers != null) {
            workers.shutdownNow();
        }
        scheduler = null;
        workers = null;
        concurrencyGate = null;
    }

    private void tick() {
        if (!running || workers == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (durationSec > 0 && now - startedAt >= durationSec * 1000) {
            stop();
            return;
        }
        double rps = effectiveRate(now);
        int batch = (int) Math.max(1, Math.round(rps / (1000.0 / TICK_MS)));
        for (int i = 0; i < batch; i++) {
            fire();
        }
        if (running) {
            scheduler.schedule(this::tick, TICK_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void fire() {
        if (!running || concurrencyGate == null || workers == null) {
            return;
        }
        if (!concurrencyGate.tryAcquire()) {
            return; // concurrency cap reached; shed this unit
        }
        String client = "client-" + Math.floorMod(clientCursor.getAndIncrement(), numClients);
        workers.submit(() -> {
            try {
                engine.route(RequestContext.of(client));
            } finally {
                if (concurrencyGate != null) {
                    concurrencyGate.release();
                }
            }
        });
    }

    /** Rate shape per pattern, given elapsed time. */
    private double effectiveRate(long nowMs) {
        double base = ratePerSec;
        double t = (nowMs - startedAt) / 1000.0;
        return switch (pattern) {
            case "sine" -> base * (0.4 + 0.6 * Math.max(0, Math.sin(2 * Math.PI * t / 20)));
            case "burst" -> {
                double wave = 0.5 + 0.5 * Math.sin(2 * Math.PI * t / 10);
                yield base * (0.4 + 1.6 * wave * wave);
            }
            case "spike" -> {
                double phase = t % 12;
                yield phase < 1.5 ? base * 5.0 : base * 0.5;
            }
            default -> base; // steady
        };
    }
}