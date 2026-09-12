package com.javalb.loadbalancer.core.strategy;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Random: picks an eligible backend uniformly at random. */
public final class RandomStrategy implements LoadBalancingStrategy {

    @Override
    public String name() {
        return "random";
    }

    @Override
    public BackendNode select(List<BackendNode> eligible, RequestContext ctx) {
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
    }
}