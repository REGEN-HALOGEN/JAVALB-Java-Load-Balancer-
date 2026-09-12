package com.javalb.loadbalancer.core.strategy;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Smooth (nginx-style) weighted round robin.
 *
 * <p>On every pick each backend accumulates {@code weight}; the backend with the
 * highest accumulated value wins and is reduced by the sum of all weights. This
 * preserves the configured weight ratios in the long run while spreading bursts
 * evenly instead of sending them all to one backend at a time.
 */
public final class WeightedRoundRobin implements LoadBalancingStrategy {

    private final Map<String, Double> current = new HashMap<>();

    @Override
    public String name() {
        return "weighted-round-robin";
    }

    @Override
    public synchronized BackendNode select(List<BackendNode> eligible, RequestContext ctx) {
        if (eligible.isEmpty()) {
            return null;
        }
        long totalWeight = 0;
        BackendNode best = null;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (BackendNode n : eligible) {
            double c = current.getOrDefault(n.id(), 0.0) + n.weight();
            current.put(n.id(), c);
            totalWeight += n.weight();
            if (best == null || c > bestVal) {
                best = n;
                bestVal = c;
            }
        }
        if (best == null) {
            return null;
        }
        current.put(best.id(), current.get(best.id()) - totalWeight);
        return best;
    }
}