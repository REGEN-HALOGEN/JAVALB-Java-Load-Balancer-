package com.javalb.loadbalancer;

import com.javalb.loadbalancer.core.BackendNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendNodeTest {

    @Test
    void passiveTrafficFailuresTakeNodeDown() {
        BackendNode node = new BackendNode("n", 1, 0, 0, 0, 10);
        assertEquals(BackendNode.State.HEALTHY, node.state());
        node.onTrafficFailure(3);
        node.onTrafficFailure(3);
        assertEquals(BackendNode.State.HEALTHY, node.state());
        node.onTrafficFailure(3);
        assertEquals(BackendNode.State.DOWN, node.state());
    }

    @Test
    void downNodeRecoversAfterSuccessesAndHealthyProbes() {
        BackendNode node = new BackendNode("n", 1, 0, 0, 0, 10);
        node.kill();
        assertEquals(BackendNode.State.DOWN, node.state());
        node.onProbeResult(true, 3, 2);
        assertEquals(BackendNode.State.DOWN, node.state());
        node.onProbeResult(true, 3, 2);
        assertEquals(BackendNode.State.HEALTHY, node.state());
    }

    @Test
    void healthyNodeGoesDownAfterConsecutiveFailedProbes() {
        BackendNode node = new BackendNode("n", 1, 0, 0, 0, 10);
        node.onProbeResult(false, 3, 2);
        node.onProbeResult(false, 3, 2);
        assertEquals(BackendNode.State.HEALTHY, node.state());
        node.onProbeResult(false, 3, 2);
        assertEquals(BackendNode.State.DOWN, node.state());
    }

    @Test
    void pauseAndResumeToggleDraining() {
        BackendNode node = new BackendNode("n", 1, 0, 0, 0, 10);
        node.pause();
        assertEquals(BackendNode.State.DRAINING, node.state());
        assertTrue(!node.isHealthy());
        node.resume();
        assertEquals(BackendNode.State.HEALTHY, node.state());
    }

    @Test
    void ewmaLatencyTracksRecentSamples() {
        BackendNode node = new BackendNode("n", 1, 0, 0, 0, 10);
        node.recordSuccess(100);
        assertTrue(node.ewmaLatencyMs() >= 90, "EWMA after one sample should be ~100, was " + node.ewmaLatencyMs());
        node.recordSuccess(10);
        node.recordSuccess(10);
        assertTrue(node.ewmaLatencyMs() < 70, "EWMA should drift toward fast samples, was " + node.ewmaLatencyMs());
    }
}