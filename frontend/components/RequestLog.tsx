"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { ListOrdered } from "lucide-react";
import { useLiveRequests } from "@/lib/store";
import { fmtMs, fmtTime } from "@/lib/format";

export default function RequestLog() {
  const requests = useLiveRequests();
  const list = [...requests].reverse().slice(0, 150);

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5 flex flex-col">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-200 mb-3">
        <ListOrdered size={15} className="text-sky-400" />
        Request log
        <span className="text-slate-500 font-normal">(last {list.length || 0})</span>
      </h2>
      <div className="space-y-1 overflow-y-auto max-h-105 pr-1" style={{ maxHeight: 420 }}>
        {list.map((r) => (
          <div
            key={r.id}
            className="flex items-center gap-3 text-xs px-2 py-1.5 rounded-lg hover:bg-slate-800/40 transition"
          >
            <span className="tabular-nums text-slate-500 shrink-0 w-14">{fmtTime(r.ts)}</span>
            <span
              className={`shrink-0 px-1.5 py-0.5 rounded-full text-[10px] font-semibold ${
                r.status >= 400
                  ? "bg-rose-500/15 text-rose-300"
                  : "bg-emerald-500/15 text-emerald-300"
              }`}
            >
              {r.status}
            </span>
            <span className="shrink-0 text-slate-200 font-medium max-w-24 truncate">{r.node ?? "—"}</span>
            <span className="shrink-0 tabular-nums text-slate-400 w-16">{fmtMs(r.latencyMs)}</span>
            <span className="flex-1 truncate text-slate-500">{r.reason}</span>
            <span className="shrink-0 text-slate-600 tabular-nums">#{r.client}</span>
          </div>
        ))}
        {list.length === 0 && (
          <div className="py-10 text-center text-xs text-slate-500">
            No requests yet. Start the generator or fire one manually.
          </div>
        )}
      </div>
    </section>
  );
}