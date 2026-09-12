export type NodeState = "HEALTHY" | "DRAINING" | "DOWN";

export interface NodeInfo {
  id: string;
  name: string;
  state: NodeState;
  weight: number;
  capacity: number;
  baseLatencyMs: number;
  errorRatePct: number;
  inFlight: number;
  totalRequests: number;
  totalSuccess: number;
  totalFailures: number;
  avgLatencyMs: number;
  ewmaLatencyMs: number;
  lastLatencyMs: number;
  errorRate: number;
}

export interface Totals {
  totalRequests: number;
  totalFailures: number;
  failureRatePct: number;
  avgLatencyMs: number;
  reqRate: number;
  p50LatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  rateSeries: number[];
  p50Series: number[];
  p95Series: number[];
  p99Series: number[];
}

export interface LoadStatus {
  running: boolean;
  ratePerSec: number;
  concurrency: number;
  pattern: string;
  numClients: number;
  startedAt?: number;
}

export interface Snapshot {
  type: "snapshot";
  ts: number;
  algorithm: string;
  stickyEnabled: boolean;
  stickyMappings: number;
  healthIntervalMs: number;
  nodes: NodeInfo[];
  totals: Totals;
  load: LoadStatus;
}

export interface RequestEvent {
  type: "request";
  id: string;
  ts: number;
  client: string;
  path: string;
  algorithm: string;
  status: number;
  node: string | null;
  latencyMs: number;
  inFlight: number;
  reason: string;
}

/** Row returned by Supabase / Java experiments endpoint (snake_case columns). */
export interface Experiment {
  id: string;
  name: string;
  algorithm: string | null;
  config?: unknown;
  started_at: string | null;
  ended_at: string | null;
  duration_ms: number | null;
  total_requests: number | null;
  avg_latency_ms: number | null;
  p95_latency_ms: number | null;
  error_rate: number | null;
  per_node?: unknown;
}

export interface AlgorithmMeta {
  name: string;
  title: string;
  desc: string;
  formula: string;
}