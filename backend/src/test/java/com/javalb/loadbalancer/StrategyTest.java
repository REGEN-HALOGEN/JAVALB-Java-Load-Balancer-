package com.javalb.loadbalancer;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;
import com.javalb.loadbalancer.core.strategy.IpHash;
import com.javalb.loadbalancer.core.strategy.LeastConnections;
import com.javalb.loadbalancer.core.strategy.LeastResponseTime;
import com.javalb.loadbalancer.core.strategy.RoundRobin;
import com.javalb.loadbalancer.core.strategy.WeightedRoundRobin;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyTest {

    private static BackendNode node(String id, int weight) {
        return new BackendNode(id, weight, 0, 0, 0, 10);
    }

    @Test
    void roundRobinRotatesInOrder() {
        BackendNode a = node("a", 1);
        BackendNode b = node("b", 1);
        RoundRobin rr = new RoundRobin();
        assertSame(a, rr.select(List.of(a, b), RequestContext.of("c")));
        assertSame(b, rr.select(List.of(a, b), RequestContext.of("c")));
        assertSame(a, rr.select(List.of(a, b), RequestContext.of("c")));
        assertSame(b, rr.select(List.of(a, b), RequestContext.of("c")));
    }

    @Test
    void weightedRoundRobinMatchesWeights() {
        BackendNode heavy = node("heavy", 3);
        BackendNode light1 = node("light1", 1);
        BackendNode light2 = node("light2", 1);
        WeightedRoundRobin wrr = new WeightedRoundRobin();
        List<BackendNode> nodes = List.of(heavy, light1, light2);

        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 60_000; i++) {
            BackendNode pick = wrr.select(nodes, RequestContext.of("c"));
            counts.merge(pick.id(), 1, Integer::sum);
        }
        int heavyPicks = counts.get("heavy");
        int lightPicks = counts.getOrDefault("light1", 0) + counts.getOrDefault("light2", 0);
        // expected 3:2 heavy:light, allow generous tolerance for smooth WRR variance
        int expectedHeavy = 60_000 * 3 / 5;
        assertTrue(Math.abs(heavyPicks - expectedHeavy) < expectedHeavy * 0.15,
                "heavy picked " + heavyPicks + " expected ~" + expectedHeavy);
        assertTrue(Math.abs(lightPicks - (60_000 - expectedHeavy)) < 60_000 * 0.15,
                "light picked " + lightPicks);
    }

    @Test
    void smoothWrrNeverStarvesLightNodesInShortTerm() {
        BackendNode heavy = node("heavy", 10);
        BackendNode light = node("light", 1);
        WeightedRoundRobin wrr = new WeightedRoundRobin();
        int lightRuns = 0;
        for (int i = 0; i < 11; i++) {
            if (wrr.select(List.of(heavy, light), RequestContext.of("c")).id().equals("light")) {
                lightRuns++;
            }
        }
        assertTrue(lightRuns >= 1, "light node should be picked at least once in a window of sum-of-weights picks");
    }

    @Test
    void leastConnectionsPicksIdlestNode() {
        BackendNode busy = node("busy", 1);
        busy.enter();
        busy.enter();
        BackendNode idle = node("idle", 1);
        LeastConnections lc = new LeastConnections();
        assertSame(idle, lc.select(List.of(busy, idle), RequestContext.of("c1")));
    }

    @Test
    void leastResponseTimePicksFastestNode() {
        BackendNode slow = node("slow", 1);
        slow.recordSuccess(100);
        slow.recordSuccess(90);
        BackendNode fast = node("fast", 1);
        fast.recordSuccess(10);
        fast.recordSuccess(12);
        LeastResponseTime lrt = new LeastResponseTime();
        assertSame(fast, lrt.select(List.of(slow, fast), RequestContext.of("c1")));
    }

    @Test
    void ipHashIsStickyForAClientWhileRingUnchanged() {
        BackendNode a = node("a", 1);
        BackendNode b = node("b", 1);
        IpHash ih = new IpHash();
        BackendNode first = ih.select(List.of(a, b), RequestContext.of("client-1"));
        for (int i = 0; i < 100; i++) {
            assertSame(first, ih.select(List.of(a, b), RequestContext.of("client-1")));
        }
    }
}