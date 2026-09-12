"use client";

import { Activity, Server } from "lucide-react";
import { LB_URL } from "@/lib/api";
import { useLiveStore } from "@/lib/store";

export default function StatusHeader() {
  const connected = useLiveStore((s) => s.connected);
  const snapshot = useLiveStore((s) => s.snapshot);

  return (
    <header className="flex flex-wrap items-center gap-4 px-6 py-4 border-b border-slate-800 bg-slate-900/60 backdrop-blur sticky top-0 z-20">
      <div className="flex items-center gap-3">
        <div className="grid place-items-center w-9 h-9 rounded-xl bg-sky-500/15 border border-sky-500/30 text-sky-400">
          <Activity size={18} />
        </div>
        <div>
          <h1 className="text-lg font-semibold tracking-tight leading-none">JavaLB</h1>
          <p className="text-xs text-slate-400 mt-1">load balancer showcase</p>
        </div>
      </div>

      <div className="flex-1" />

      {snapshot?.algorithm && (
        <span className="px-3 py-1.5 rounded-full text-xs font-medium bg-indigo-500/15 border border-indigo-500/30 text-indigo-300 capitalize">
          {snapshot.algorithm.replace(/-/g, " ")}
        </span>
      )}

      <span
        className={`flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium border ${
          connected
            ? "bg-emerald-500/10 border-emerald-500/30 text-emerald-300"
            : "bg-rose-500/10 border-rose-500/30 text-rose-300"
        }`}
      >
        <span
          className={`live-dot w-2 h-2 rounded-full ${connected ? "bg-emerald-400" : "bg-rose-400"}`}
        />
        {connected ? "live" : "reconnecting…"}
      </span>

      <span className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-full text-xs text-slate-400 border border-slate-700">
        <Server size={12} />
        {LB_URL.replace(/^https?:\/\//, "")}
      </span>
    </header>
  );
}