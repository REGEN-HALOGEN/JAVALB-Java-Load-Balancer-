package com.javalb.loadbalancer.core.strategy;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin: requests rotate through the eligible backends in order.
 * Distribution becomes unbalanced when backends differ in speed, which later
 * strategies (least-connections / least-response-time) fix.
 */
public final class RoundRobin implements LoadBalancingStrategy {

    private final AtomicLong cursor = new AtomicLong(0);

    @Override
    public String name() {
        return "round-robin";
    }

    @Override
    public BackendNode select(List<BackendNode> eligible, RequestContext ctx) {
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get((int) Long.remainderUnsigned(cursor.getAndIncrement(), eligible.size()));
    }
}