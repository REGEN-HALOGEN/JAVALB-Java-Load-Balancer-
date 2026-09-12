package com.javalb.loadbalancer.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional persistence to Supabase (Postgres) via JDBC.
 *
 * <p>Degrades to a no-op when {@code SUPABASE_DB_URL} is not set, so the load
 * balancer runs perfectly fine without any database for local development.
 */
public final class SupabaseRepo {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String jdbcUrl;
    private final boolean available;
    private final Map<String, Long> experimentStartTimes = new java.util.concurrent.ConcurrentHashMap<>();

    public SupabaseRepo() {
        String raw = System.getenv().get("SUPABASE_DB_URL");
        if (raw == null || raw.isBlank()) {
            this.available = false;
            this.jdbcUrl = null;
            System.out.println("[javalb] Supabase persistence DISABLED (SUPABASE_DB_URL not set)");
            return;
        }
        this.available = true;
        this.jdbcUrl = toJdbcUrl(raw);
        System.out.println("[javalb] Supabase persistence ENABLED");
    }

    public boolean isAvailable() {
        return available;
    }

    private static String toJdbcUrl(String url) {
        if (url.startsWith("jdbc:")) {
            return url;
        }
        try {
            URI u = new URI(url.replaceFirst("postgresql://", "http://"));
            String user = "";
            String pass = "";
            String ui = u.getUserInfo();
            if (ui != null && ui.contains(":")) {
                String[] parts = ui.split(":", 2);
                user = parts[0];
                pass = parts[1];
            } else if (ui != null) {
                user = ui;
            }
            String query = u.getQuery() == null ? "" : "&" + u.getQuery();
            int port = u.getPort() == -1 ? 5432 : u.getPort();
            return "jdbc:postgresql://" + u.getHost() + ":" + port + u.getPath()
                    + "?sslmode=require&user=" + user + "&password=" + pass + query;
        } catch (Exception e) {
            return url;
        }
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Starts a named experiment and returns its id. */
    public String beginExperiment(String name, String algorithm, Map<String, Object> config) {
        if (!available) {
            String local = "local-" + UUID.randomUUID().toString().substring(0, 8);
            experimentStartTimes.put(local, System.currentTimeMillis());
            System.out.println("[javalb] experiment '" + name + "' started (local, no db): " + local);
            return local;
        }
        String id = UUID.randomUUID().toString();
        String sql = "insert into experiments (id, name, algorithm, config, started_at) values (?, ?, ?, ?::jsonb, now())";
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(id));
            ps.setString(2, name);
            ps.setString(3, algorithm);
            ps.setString(4, toJson(config));
            ps.executeUpdate();
            experimentStartTimes.put(id, System.currentTimeMillis());
        } catch (SQLException e) {
            System.err.println("[javalb] experiment insert failed: " + e.getMessage());
        }
        return id;
    }

    /** Persists a time-series point for a running experiment. */
    public void recordPoint(String expId, Map<String, Object> snapshot) {
        if (!available || expId == null || !experimentStartTimes.containsKey(expId)) {
            return;
        }
        Map<String, Object> perNode = new LinkedHashMap<>();
        Object nodesRaw = snapshot.get("nodes");
        if (nodesRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    perNode.put(String.valueOf(m.get("id")), new LinkedHashMap<>((Map<?, ?>) m));
                }
            }
        }
        Map<String, Object> totals = asMap(snapshot.get("totals"));
        String sql = """
                insert into experiment_points
                  (experiment_id, ts, req_rate, total_p95_ms, latency_p50_ms, latency_p99_ms, per_node)
                values (?, now(), ?, ?, ?, ?, ?::jsonb)
                """;
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(expId));
            ps.setDouble(2, toDouble(totals, "reqRate", 0));
            ps.setDouble(3, toDouble(totals, "p95LatencyMs", 0));
            ps.setDouble(4, toDouble(totals, "p50LatencyMs", 0));
            ps.setDouble(5, toDouble(totals, "p99LatencyMs", 0));
            ps.setString(6, toJson(perNode));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[javalb] experiment point failed: " + e.getMessage());
        }
    }

    /** Finalizes an experiment with its aggregate totals. */
    public Map<String, Object> finishExperiment(String expId, Map<String, Object> snapshot) {
        Map<String, Object> totals = asMap(snapshot.get("totals"));
        Map<String, Object> perNode = new LinkedHashMap<>();
        Object nodesRaw = snapshot.get("nodes");
        if (nodesRaw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    perNode.put(String.valueOf(m.get("id")), new LinkedHashMap<>((Map<?, ?>) m));
                }
            }
        }
        long totalRequests = toLong(totals, "totalRequests", 0);
        double failureRate = toDouble(totals, "failureRatePct", 0);
        long avgLatency = toLong(totals, "avgLatencyMs", 0);
        double p95 = toDouble(totals, "p95LatencyMs", 0);

        Long started = experimentStartTimes.remove(expId);
        long durationMs = started == null ? 0 : System.currentTimeMillis() - started;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("experimentId", expId);
        result.put("persisted", available && started != null);
        if (!available) {
            System.out.println("[javalb] experiment finished (local, no db): "
                    + expId + " reqs=" + totalRequests + " avg=" + avgLatency + "ms err=" + failureRate + "%");
            result.put("durationMs", durationMs);
            result.put("totalRequests", totalRequests);
            result.put("avgLatencyMs", avgLatency);
            result.put("p95LatencyMs", p95);
            result.put("failureRatePct", failureRate);
            return result;
        }
        String sql = """
                update experiments
                   set ended_at = now(), duration_ms = ?, total_requests = ?,
                       avg_latency_ms = ?, p95_latency_ms = ?, error_rate = ?, per_node = ?::jsonb
                 where id = ?
                """;
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, durationMs);
            ps.setLong(2, totalRequests);
            ps.setLong(3, avgLatency);
            ps.setDouble(4, p95);
            ps.setDouble(5, failureRate);
            ps.setString(6, toJson(perNode));
            ps.setObject(7, UUID.fromString(expId));
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[javalb] experiment update failed: " + e.getMessage());
        }
        result.put("durationMs", durationMs);
        result.put("totalRequests", totalRequests);
        result.put("avgLatencyMs", avgLatency);
        result.put("p95LatencyMs", p95);
        result.put("failureRatePct", failureRate);
        return result;
    }

    /** Lists recent experiments (newest first). */
    public List<Map<String, Object>> listExperiments(int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!available) {
            return out;
        }
        String sql = """
                select id, name, algorithm, started_at, ended_at, duration_ms,
                       total_requests, avg_latency_ms, p95_latency_ms, error_rate, per_node
                  from experiments order by started_at desc limit ?
                """;
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, Math.min(limit, 200)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("name", rs.getString("name"));
                    row.put("algorithm", rs.getString("algorithm"));
                    row.put("started_at", rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toInstant().toString());
                    row.put("ended_at", rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant().toString());
                    row.put("duration_ms", rs.getLong("duration_ms"));
                    row.put("total_requests", rs.getLong("total_requests"));
                    row.put("avg_latency_ms", rs.getDouble("avg_latency_ms"));
                    row.put("p95_latency_ms", rs.getDouble("p95_latency_ms"));
                    row.put("error_rate", rs.getDouble("error_rate"));
                    out.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[javalb] list experiments failed: " + e.getMessage());
        }
        return out;
    }

    public void close() {
        // DriverManager connections are pooled/closed per use; nothing to release here.
    }

    private static Map<String, Object> asMap(Object o) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (o instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static double toDouble(Map<String, Object> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return def;
    }

    private static long toLong(Map<String, Object> m, String key, long def) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return def;
    }
}