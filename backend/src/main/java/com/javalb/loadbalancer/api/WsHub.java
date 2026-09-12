package com.javalb.loadbalancer.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.api.Session;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Fan-out of live messages to every connected dashboard over WebSocket.
 *
 * <p>The control plane stays on regular REST; this channel is dedicated to
 * pushing metric snapshots and per-request events. Jetty sessions are
 * thread-safe for remote sends, so broadcasts can happen from any worker thread.
 */
public final class WsHub {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long SNAPSHOT_INTERVAL_MS = 500;

    private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService broadcaster =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-broadcaster");
                t.setDaemon(true);
                return t;
            });

    private volatile Supplier<Map<String, Object>> snapshotProvider = Map::of;

    private WsHub() {
    }

    public static WsHub create() {
        WsHub hub = new WsHub();
        hub.broadcaster.scheduleWithFixedDelay(hub::broadcastSnapshot, 0,
                SNAPSHOT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        return hub;
    }

    public WsHub setSnapshotProvider(Supplier<Map<String, Object>> provider) {
        this.snapshotProvider = provider == null ? Map::of : provider;
        return this;
    }

    /** Wire to {@code ws.onConnect}. */
    public void onConnect(Session session) {
        sessions.add(session);
        broadcastSnapshot(session);
    }

    /** Wire to {@code ws.onClose} and {@code ws.onError}. */
    public void onDisconnect(Session session) {
        sessions.remove(session);
    }

    /** Broadcast an arbitrary event payload (e.g. per-request events). */
    public void broadcast(Map<String, Object> payload) {
        if (payload == null || sessions.isEmpty()) {
            return;
        }
        String json = toJson(payload);
        if (json == null) {
            return;
        }
        for (Session session : sessions) {
            if (!send(session, json)) {
                sessions.remove(session);
            }
        }
    }

    private void broadcastSnapshot() {
        if (sessions.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>(snapshotProvider.get());
        payload.put("type", "snapshot");
        broadcast(payload);
    }

    private void broadcastSnapshot(Session only) {
        Map<String, Object> payload = new LinkedHashMap<>(snapshotProvider.get());
        payload.put("type", "snapshot");
        send(only, toJson(payload));
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean send(Session session, String json) {
        if (json == null) {
            return true;
        }
        try {
            session.getRemote().sendString(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int connections() {
        return sessions.size();
    }
}