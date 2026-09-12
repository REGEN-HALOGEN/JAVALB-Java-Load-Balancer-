package com.javalb.loadbalancer;

import com.javalb.loadbalancer.core.LoadBalancerEngine;
import com.javalb.loadbalancer.core.RequestContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSmokeTest {

    @Test
    void routesRequestThroughMockBackend() {
        LoadBalancerEngine engine = new LoadBalancerEngine();
        engine.addNode("t1", 1, 0, 0, 0, 10);
        try {
            Map<String, Object> r = engine.route(RequestContext.of("smoke-client"));
            assertEquals(200, r.get("status"));
            assertEquals("t1", r.get("node"));
        } finally {
            engine.removeNode("t1");
        }
    }

    @Test
    void returns503WhenNoHealthyBackends() {
        LoadBalancerEngine engine = new LoadBalancerEngine();
        engine.addNode("t1", 1, 0, 0, 0, 10);
        try {
            var n = engine.find("t1");
            assert n != null;
            n.kill();
            Map<String, Object> r = engine.route(RequestContext.of("smoke-client"));
            assertEquals(503, r.get("status"));
            assertNull(r.get("node"));
        } finally {
            engine.removeNode("t1");
        }
    }

    @Test
    void strategyCanBeSwitched() {
        LoadBalancerEngine engine = new LoadBalancerEngine();
        assertEquals("round-robin", engine.strategyName());
        assertEquals("least-connections", engine.setStrategy("least-connections"));
        assertEquals("least-connections", engine.strategyName());
    }

    @Test
    void stickySessionPinsClientToNode() {
        LoadBalancerEngine engine = new LoadBalancerEngine();
        engine.addNode("sa", 1, 0, 0, 0, 10);
        engine.addNode("sb", 1, 0, 0, 0, 10);
        engine.setStickyEnabled(true);
        try {
            String firstNode = (String) engine.route(RequestContext.of("pinned-client")).get("node");
            for (int i = 0; i < 20; i++) {
                Map<String, Object> r = engine.route(RequestContext.of("pinned-client"));
                assertEquals(firstNode, r.get("node"));
            }
            assertTrue(engine.sticky().pinnedNode("pinned-client") != null);
        } finally {
            engine.removeNode("sa");
            engine.removeNode("sb");
        }
    }
}