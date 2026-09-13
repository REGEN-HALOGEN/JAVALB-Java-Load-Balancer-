import { create } from "zustand";
import { useShallow } from "zustand/react/shallow";
import type { RequestEvent, Snapshot } from "./types";

const MAX_REQUESTS = 400;
const REQUEST_BATCH_MS = 150; // batch rapid request events

interface LiveState {
  connected: boolean;
  snapshot: Snapshot | null;
  requests: RequestEvent[];
  setConnected: (v: boolean) => void;
  pushSnapshot: (s: Snapshot) => void;
  pushRequest: (r: RequestEvent) => void;
  _flushRequestBatch: () => void;
}

const emptySnapshot: Snapshot = {
  type: "snapshot",
  ts: 0,
  algorithm: "round-robin",
  stickyEnabled: false,
  stickyMappings: 0,
  healthIntervalMs: 2000,
  nodes: [],
  totals: {
    totalRequests: 0,
    totalFailures: 0,
    failureRatePct: 0,
    avgLatencyMs: 0,
    reqRate: 0,
    p50LatencyMs: 0,
    p95LatencyMs: 0,
    p99LatencyMs: 0,
    rateSeries: [],
    p50Series: [],
    p95Series: [],
    p99Series: [],
  },
  load: { running: false, ratePerSec: 60, concurrency: 24, pattern: "steady", numClients: 12 },
};

export const useLiveStore = create<LiveState>()(
  (set, get) => {
    let batch: RequestEvent[] = [];
    let batchTimer: ReturnType<typeof setTimeout> | null = null;

    const flush = () => {
      if (batch.length === 0) return;
      const toAdd = [...batch];
      batch = [];
      if (batchTimer) {
        clearTimeout(batchTimer);
        batchTimer = null;
      }
      set((st) => ({ requests: [...st.requests, ...toAdd].slice(-MAX_REQUESTS) }));
    };

    return {
      connected: false,
      snapshot: emptySnapshot,
      requests: [],
      setConnected: (v) => set({ connected: v }),
      pushSnapshot: (s) => set({ snapshot: s }),
      pushRequest: (r) => {
        batch.push(r);
        if (!batchTimer) {
          batchTimer = setTimeout(flush, REQUEST_BATCH_MS);
        }
      },
      _flushRequestBatch: flush,
    };
  },
);

export const useLiveNodes = () => useLiveStore(useShallow((s) => s.snapshot?.nodes ?? []));
export const useLiveSnapshot = () => useLiveStore(useShallow((s) => s.snapshot));
export const useLiveRequests = () => useLiveStore(useShallow((s) => s.requests));
export const useLiveConnected = () => useLiveStore(useShallow((s) => s.connected));