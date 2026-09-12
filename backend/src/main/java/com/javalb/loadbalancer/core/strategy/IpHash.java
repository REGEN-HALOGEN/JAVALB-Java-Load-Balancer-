package com.javalb.loadbalancer.core.strategy;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Client IP hash using a consistent-hash ring.
 *
 * <p>Each backend is placed on a 64-bit ring with {@value VIRTUAL_NODES} virtual
 * nodes; a client hashes onto the ring and lands on the closest backend. This
 * gives deterministic client affinity while only remapping a small number of
 * clients when the backend set changes.
 */
public final class IpHash implements LoadBalancingStrategy {

    private static final int VIRTUAL_NODES = 512;

    private volatile NavigableMap<Long, BackendNode> ring = new TreeMap<>();
    private volatile String ringKey = "";

    @Override
    public String name() {
        return "ip-hash";
    }

    @Override
    public synchronized BackendNode select(List<BackendNode> eligible, RequestContext ctx) {
        if (eligible.isEmpty()) {
            return null;
        }
        rebuild(eligible);
        long h = hash(ctx.clientId());
        NavigableMap.Entry<Long, BackendNode> entry = ring.ceilingEntry(h);
        if (entry == null) {
            entry = ring.firstEntry();
        }
        return entry == null ? null : entry.getValue();
    }

    private void rebuild(List<BackendNode> eligible) {
        String key = eligible.stream().map(BackendNode::id).sorted().collect(Collectors.joining("|"));
        if (key.equals(ringKey)) {
            return;
        }
        TreeMap<Long, BackendNode> next = new TreeMap<>();
        for (BackendNode n : eligible) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                next.put(hash(n.id() + "#" + i), n);
            }
        }
        ring = next;
        ringKey = key;
    }

    /** SplitMix64-style string hash. */
    private static long hash(String s) {
        long h = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < s.length(); i++) {
            h = (h ^ s.charAt(i)) * 0x100000001B3L;
        }
        h ^= h >>> 31;
        h *= 0x100000001B3L;
        h ^= h >>> 29;
        return h;
    }
}