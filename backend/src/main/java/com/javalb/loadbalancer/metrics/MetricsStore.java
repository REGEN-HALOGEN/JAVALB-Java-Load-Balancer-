package com.javalb.loadbalancer.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global metrics: request/failure counters, per-second request-rate history
 * (ring buffer) and a latency reservoir for p50/p95/p99 percentiles.
 */
public final class MetricsStore {

    private static final int RING_SECONDS = 300;
    private static final int MAX_LATENCY_SAMPLES = 4000;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalFailures = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final ConcurrentLinkedDeque<Long> latencySamples = new ConcurrentLinkedDeque<>();

    private final Object lock = new Object();
    private final java.util.ArrayDeque<Long> rateRing = new java.util.ArrayDeque<>(RING_SECONDS);
    private final java.util.ArrayDeque<Double> p50Ring = new java.util.ArrayDeque<>(RING_SECONDS);
    private final java.util.ArrayDeque<Double> p95Ring = new java.util.ArrayDeque<>(RING_SECONDS);
    private final java.util.ArrayDeque<Double> p99Ring = new java.util.ArrayDeque<>(RING_SECONDS);
    private long secondCount;
    private long secondStart;

    public void recordRequest(boolean success, long latencyMs) {
        totalRequests.incrementAndGet();
        if (!success) {
            totalFailures.incrementAndGet();
        }
        totalLatencyMs.addAndGet(latencyMs);
        latencySamples.addLast(latencyMs);
        while (latencySamples.size() > MAX_LATENCY_SAMPLES) {
            latencySamples.pollFirst();
        }
        synchronized (lock) {
            long nowSec = System.currentTimeMillis() / 1000;
            if (secondStart == 0) {
                secondStart = nowSec;
                secondCount = 1;
            } else if (nowSec == secondStart) {
                secondCount++;
            } else {
                pushRate(secondCount, nowSec);
                secondCount = 1;
                secondStart = nowSec;
            }
        }
    }

    /** Called on snapshot so the current (partial) second is folded in. */
    private void flushCurrentSecond() {
        synchronized (lock) {
            if (secondStart != 0) {
                long nowSec = System.currentTimeMillis() / 1000;
                if (nowSec != secondStart) {
                    pushRate(secondCount, nowSec);
                    secondCount = 1;
                    secondStart = nowSec;
                }
            }
        }
    }

    private void pushRate(long count, long nowSec) {
        rateRing.addLast(count);
        while (rateRing.size() > RING_SECONDS) {
            rateRing.pollFirst();
        }
        List<Long> samples = new ArrayList<>(latencySamples);
        if (samples.isEmpty()) {
            p50Ring.addLast(0.0);
            p95Ring.addLast(0.0);
            p99Ring.addLast(0.0);
        } else {
            Collections.sort(samples);
            p50Ring.addLast(percentile(samples, 0.50));
            p95Ring.addLast(percentile(samples, 0.95));
            p99Ring.addLast(percentile(samples, 0.99));
        }
        trimRing(p50Ring);
        trimRing(p95Ring);
        trimRing(p99Ring);
    }

    private static void trimRing(java.util.ArrayDeque<Double> ring) {
        while (ring.size() > RING_SECONDS) {
            ring.pollFirst();
        }
    }

    private static double percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil(p * sorted.size()) - 1);
        return sorted.get(Math.max(0, idx));
    }

    public void reset() {
        totalRequests.set(0);
        totalFailures.set(0);
        totalLatencyMs.set(0);
        latencySamples.clear();
        synchronized (lock) {
            rateRing.clear();
            p50Ring.clear();
            p95Ring.clear();
            p99Ring.clear();
            secondCount = 0;
            secondStart = 0;
        }
    }

    public Map<String, Object> snapshot() {
        flushCurrentSecond();
        synchronized (lock) {
            double reqRate = rateRing.isEmpty() ? 0 : rateRing.stream().mapToLong(Long::longValue).average().orElse(0);
            long reqs = totalRequests.get();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("totalRequests", reqs);
            out.put("totalFailures", totalFailures.get());
            out.put("failureRatePct", round1(reqs == 0 ? 0 : totalFailures.get() * 100.0 / reqs));
            out.put("avgLatencyMs", reqs == 0 ? 0 : Math.round(totalLatencyMs.get() * 1.0 / reqs));
            out.put("reqRate", Math.round(reqRate));
            out.put("p50LatencyMs", round1(latest(p50Ring)));
            out.put("p95LatencyMs", round1(latest(p95Ring)));
            out.put("p99LatencyMs", round1(latest(p99Ring)));
            out.put("rateSeries", new ArrayList<>(rateRing));
            out.put("p50Series", new ArrayList<>(p50Ring));
            out.put("p95Series", new ArrayList<>(p95Ring));
            out.put("p99Series", new ArrayList<>(p99Ring));
            return out;
        }
    }

    private static double latest(java.util.ArrayDeque<Double> ring) {
        return ring.isEmpty() ? 0 : ring.peekLast();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}