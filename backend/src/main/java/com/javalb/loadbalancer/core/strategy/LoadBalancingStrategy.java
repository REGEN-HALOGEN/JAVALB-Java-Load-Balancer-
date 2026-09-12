package com.javalb.loadbalancer.core.strategy;

import com.javalb.loadbalancer.core.BackendNode;
import com.javalb.loadbalancer.core.RequestContext;

import java.util.List;

/**
 * Strategy contract for picking the target backend for a request.
 * Implementations are singletons held by the shared {@link Strategies} registry.
 */
public interface LoadBalancingStrategy {

    /** Stable identifier used by the control plane and the dashboard. */
    String name();

    /** Pick one of the eligible (healthy) backends for the given request context. Never called with an empty list. */
    BackendNode select(List<BackendNode> eligible, RequestContext ctx);
}