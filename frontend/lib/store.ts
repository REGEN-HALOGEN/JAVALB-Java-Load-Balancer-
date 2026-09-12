import { create } from "zustand";
import type { RequestEvent, Snapshot } from "./types";

const MAX_REQUESTS = 400;

interface LiveState {
  connected: boolean;
  snapshot: Snapshot | null;
  requests: RequestEvent[];
  setConnected: (v: boolean) => void;
  pushSnapshot: (s: Snapshot) => void;
  pushRequest: (r: RequestEvent) => void;
}

export const useLiveStore = create<LiveState>((set) => ({
  connected: false,
  snapshot: null,
  requests: [],
  setConnected: (v) => set({ connected: v }),
  pushSnapshot: (s) => set({ snapshot: s }),
  pushRequest: (r) => set((st) => ({ requests: [...st.requests, r].slice(-MAX_REQUESTS) })),
}));