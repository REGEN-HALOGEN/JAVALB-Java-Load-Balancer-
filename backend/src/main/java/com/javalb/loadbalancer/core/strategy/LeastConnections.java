package com.javalb.loadbalancer.core.strategy;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.Comparator;
import java.util.List;

/**
 * Least connections: prefers the backend with the fewest in-flight requests.
 * This is the strategy that visibly shines when one backend is saturated.
 */
public final class LeastConnections implements LoadBalancingStrategy {

    @Override
    public String name() {
        return "least-connections";
    }

    @Override
    public BackendNode select(List<BackendNode> eligible, RequestContext ctx) {
        return eligible.stream()
                .min(Comparator.comparingInt(BackendNode::inFlight).thenComparing(BackendNode::id))
                .orElse(null);
    }
}