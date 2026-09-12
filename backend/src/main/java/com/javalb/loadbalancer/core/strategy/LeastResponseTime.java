package com.javalb.loadbalancer.core.strategy;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.Comparator;
import java.util.List;

/**
 * Least response time: prefers the backend with the lowest exponentially
 * weighted moving average of measured latency. Over time it automatically
 * shifts traffic away from slow (e.g. high-error / overloaded) backends.
 */
public final class LeastResponseTime implements LoadBalancingStrategy {

    @Override
    public String name() {
        return "least-response-time";
    }

    @Override
    public BackendNode select(List<BackendNode> eligible, RequestContext ctx) {
        return eligible.stream()
                .min(Comparator.comparingDouble(BackendNode::ewmaLatencyMs).thenComparing(BackendNode::id))
                .orElse(null);
    }
}