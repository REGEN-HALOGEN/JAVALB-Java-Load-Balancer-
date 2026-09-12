import type { Experiment, RequestEvent, Snapshot } from "./types";

const LB_URL = (process.env.NEXT_PUBLIC_LB_URL ?? "http://localhost:8080").replace(/\/+$/, "");
export const LB_WS_URL = `${LB_URL.replace(/^http/, "ws")}/ws/events`;
export { LB_URL };

function assertOk(res: Response, path: string) {
  if (!res.ok) {
    throw new Error(`GET ${path} -> HTTP ${res.status}`);
  }
}

async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(`${LB_URL}${path}`);
  assertOk(res, path);
  return (await res.json()) as T;
}

async function apiPost<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${LB_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body ?? {}),
  });
  assertOk(res, path);
  return (await res.json()) as T;
}

export interface BackendUpdate {
  id: string;
  action?: "add" | "remove" | "update";
  op?: "pause" | "resume" | "kill" | "revive";
  weight?: number;
  baseLatencyMs?: number;
  sigmaLatencyMs?: number;
  errorRatePct?: number;
  capacity?: number;
}

export const api = {
  state: () => apiGet<Snapshot>("/api/state"),

  config: (body: {
    strategy?: string;
    stickyEnabled?: boolean;
    healthIntervalMs?: number;
    backends?: BackendUpdate[];
  }) => apiPost<Snapshot>("/api/config", body),

  load: (body: {
    running: boolean;
    ratePerSec?: number;
    concurrency?: number;
    pattern?: string;
    numClients?: number;
    durationSec?: number;
  }) => apiPost<Snapshot>("/api/load", body),

  proxy: (body: { clientId?: string; path?: string }) =>
    apiPost<RequestEvent>("/api/proxy", body),

  startExperiment: (name: string) =>
    apiPost<{ experimentId: string; started: boolean }>("/api/experiment", { name }),

  stopExperiment: () =>
    apiPost<{ experimentId?: string; persisted?: boolean; totalRequests?: number }>(
      "/api/experiment/stop",
      {},
    ),

  resetStats: () => apiPost<{ ok: boolean }>("/api/reset-stats", {}),

  experiments: () => apiGet<Experiment[]>("/api/experiments"),
};