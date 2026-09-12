package com.javalb.loadbalancer.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A backend node: configurable behavior (weight, latency, error rate, capacity)
 * plus live runtime statistics. Behavior is read by the mock server at request
 * time, so admin changes apply instantly to new traffic.
 *
 * <p>State machine: {@code HEALTHY -> DRAINING -> HEALTHY} via admin pause/resume,
 * and {@code HEALTHY -> DOWN -> HEALTHY} via health probing / passive failures.
 */
public final class BackendNode {

    public enum State { HEALTHY, DRAINING, DOWN }

    private final String id;
    private final String name;
    private final MockBackendServer server;

    private volatile int weight;
    private volatile long baseLatencyMs;
    private volatile double sigmaLatencyMs;
    private volatile double errorRatePct;
    private volatile int capacity;

    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalSuccess = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    private volatile double ewmaLatencyMs;
    private volatile long lastLatencyMs;

    private volatile State state = State.HEALTHY;
    private int consecutiveFailures;
    private int consecutiveSuccesses;

    public BackendNode(String id, int weight, long baseLatencyMs, double sigmaLatencyMs,
                       double errorRatePct, int capacity) {
        this.id = id;
        this.name = "backend-" + id;
        this.weight = Math.max(1, weight);
        this.baseLatencyMs = Math.max(0, baseLatencyMs);
        this.sigmaLatencyMs = Math.max(0, sigmaLatencyMs);
        this.errorRatePct = Math.max(0, Math.min(100, errorRatePct));
        this.capacity = Math.max(1, capacity);
        this.server = new MockBackendServer(this);
    }

    public String id()            { return id; }
    public String name()          { return name; }
    public int weight()           { return weight; }
    public long baseLatencyMs()   { return baseLatencyMs; }
    public double sigmaLatencyMs(){ return sigmaLatencyMs; }
    public double errorRatePct()  { return errorRatePct; }
    public int capacity()         { return capacity; }
    public State state()          { return state; }
    public boolean isHealthy()    { return state == State.HEALTHY; }
    public int inFlight()         { return inFlight.get(); }
    public double ewmaLatencyMs() { return ewmaLatencyMs; }
    public long lastLatencyMs()   { return lastLatencyMs; }
    public MockBackendServer server() { return server; }

    public void setWeight(int w)           { this.weight = Math.max(1, w); }
    public void setBaseLatencyMs(long l)   { this.baseLatencyMs = Math.max(0, l); }
    public void setSigmaLatencyMs(double s){ this.sigmaLatencyMs = Math.max(0, s); }
    public void setErrorRatePct(double p)  { this.errorRatePct = Math.max(0, Math.min(100, p)); }
    public void setCapacity(int c)         { this.capacity = Math.max(1, c); this.server.setCapacity(c); }

    /** Marks a request as started. Returns the updated in-flight count. */
    public int enter() { return inFlight.incrementAndGet(); }

    /** Marks a request as finished. */
    public void leave() { inFlight.decrementAndGet(); }

    public void recordSuccess(long latencyMs) {
        totalRequests.incrementAndGet();
        totalSuccess.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        updateEwma(latencyMs);
        onTrafficSuccess();
    }

    public void recordFailure(long latencyMs) {
        totalRequests.incrementAndGet();
        totalFailures.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        updateEwma(latencyMs);
    }

    /** Passive health: a proxied request came back with a 5xx. */
    public synchronized void onTrafficFailure(int threshold) {
        consecutiveFailures++;
        consecutiveSuccesses = 0;
        if (state == State.HEALTHY && consecutiveFailures >= threshold) {
            state = State.DOWN;
        }
    }

    public synchronized void onTrafficSuccess() {
        consecutiveSuccesses++;
        consecutiveFailures = 0;
    }

    /** Health-probe result from {@link HealthChecker}. */
    public synchronized void onProbeResult(boolean ok, int failureThreshold, int successThreshold) {
        if (ok) {
            consecutiveSuccesses++;
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
            consecutiveSuccesses = 0;
        }
        if (state == State.HEALTHY && consecutiveFailures >= failureThreshold) {
            state = State.DOWN;
        } else if (state == State.DOWN && consecutiveSuccesses >= successThreshold) {
            state = State.HEALTHY;
        }
    }

    /** Admin: stop accepting new traffic (in-flight requests drain). */
    public void pause() { state = State.DRAINING; }

    /** Admin: resume accepting traffic. */
    public void resume() { state = State.HEALTHY; consecutiveFailures = 0; consecutiveSuccesses = 0; }

    /** Admin: force the node down. */
    public void kill() { state = State.DOWN; consecutiveFailures = Integer.MAX_VALUE / 2; }

    /** Admin: force the node back to healthy. */
    public void revive() { state = State.HEALTHY; consecutiveFailures = 0; consecutiveSuccesses = 0; }

    public void resetStats() {
        totalRequests.set(0);
        totalSuccess.set(0);
        totalFailures.set(0);
        totalLatencyMs.set(0);
        ewmaLatencyMs = 0;
        lastLatencyMs = 0;
        inFlight.set(0);
        consecutiveFailures = 0;
        consecutiveSuccesses = 0;
    }

    private void updateEwma(long latencyMs) {
        lastLatencyMs = latencyMs;
        if (ewmaLatencyMs <= 0) {
            ewmaLatencyMs = latencyMs;
        } else {
            ewmaLatencyMs = ewmaLatencyMs * 0.8 + latencyMs * 0.2;
        }
    }

    public long avgLatencyMs() {
        long reqs = totalRequests.get();
        return reqs == 0 ? 0 : totalLatencyMs.get() / reqs;
    }

    /** JSON-friendly snapshot of this node's config + stats. */
    public Map<String, Object> snapshot() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("state", state.name());
        m.put("weight", weight);
        m.put("capacity", capacity);
        m.put("baseLatencyMs", baseLatencyMs);
        m.put("errorRatePct", errorRatePct);
        m.put("inFlight", inFlight.get());
        m.put("totalRequests", totalRequests.get());
        m.put("totalSuccess", totalSuccess.get());
        m.put("totalFailures", totalFailures.get());
        m.put("avgLatencyMs", avgLatencyMs());
        m.put("ewmaLatencyMs", Math.round(ewmaLatencyMs));
        m.put("lastLatencyMs", lastLatencyMs);
        m.put("errorRate", totalRequests.get() == 0 ? 0.0 : totalFailures.get() * 100.0 / totalRequests.get());
        return m;
    }
}