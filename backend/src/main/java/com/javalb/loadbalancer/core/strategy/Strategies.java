package com.javalb.loadbalancer.core.strategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of all built-in balancing strategies. */
public final class Strategies {

    private static final Map<String, LoadBalancingStrategy> ALL;

    static {
        Map<String, LoadBalancingStrategy> m = new LinkedHashMap<>();
        LoadBalancingStrategy[] all = {
                new RoundRobin(),
                new WeightedRoundRobin(),
                new LeastConnections(),
                new LeastResponseTime(),
                new IpHash(),
                new RandomStrategy()
        };
        for (LoadBalancingStrategy s : all) {
            m.put(s.name(), s);
        }
        ALL = Collections.unmodifiableMap(m);
    }

    private Strategies() {
    }

    public static LoadBalancingStrategy get(String name) {
        LoadBalancingStrategy s = ALL.get(name);
        return s != null ? s : ALL.get("round-robin");
    }

    public static List<String> names() {
        return List.copyOf(ALL.keySet());
    }
}