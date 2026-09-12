"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { HeartPulse, Pause, Play, Plus, Skull, Trash2 } from "lucide-react";
import { api } from "@/lib/api";
import { useLiveStore } from "@/lib/store";
import { nodeColor } from "@/lib/algorithms";
import { fmtNum } from "@/lib/format";
import type { NodeInfo } from "@/lib/types";

interface RowDraft {
  weight: number;
  latency: number;
  error: number;
  capacity: number;
}

const stateLabel: Record<NodeInfo["state"], string> = {
  HEALTHY: "healthy",
  DRAINING: "draining",
  DOWN: "down",
};

export default function BackendTable() {
  const nodes = useLiveStore((s) => s.snapshot?.nodes ?? []);
  const [drafts, setDrafts] = useState<Record<string, RowDraft>>({});
  const dragTimers = useRef<Record<string, ReturnType<typeof setTimeout> | null>>({});
  const [showAdd, setShowAdd] = useState(false);
  const [addForm, setAddForm] = useState({ id: "", weight: 1, latency: 20, error: 1, capacity: 20 });

  const draftOf = (n: NodeInfo): RowDraft =>
    drafts[n.id] ?? {
      weight: n.weight,
      latency: n.baseLatencyMs,
      error: n.errorRatePct,
      capacity: n.capacity,
    };

  const push = (specs: Parameters<typeof api.config>[0]) =>
    api.config(specs).catch((e) => console.error(e));

  const commit = (n: NodeInfo, patch: Partial<RowDraft>) => {
    const merged = { ...draftOf(n), ...patch };
    setDrafts((d) => ({ ...d, [n.id]: merged }));
    const pending = dragTimers.current[n.id];
    if (pending) clearTimeout(pending);
    dragTimers.current[n.id] = setTimeout(() => {
      push({
        backends: [
          {
            id: n.id,
            action: "update",
            weight: merged.weight,
            baseLatencyMs: merged.latency,
            errorRatePct: merged.error,
            capacity: merged.capacity,
          },
        ],
      });
    }, 250);
  };

  const togglePause = (n: NodeInfo) =>
    push({ backends: [{ id: n.id, action: "update", op: n.state === "DRAINING" ? "resume" : "pause" }] });
  const toggleHealth = (n: NodeInfo) =>
    push({ backends: [{ id: n.id, action: "update", op: n.state === "DOWN" ? "revive" : "kill" }] });
  const remove = (n: NodeInfo) => push({ backends: [{ id: n.id, action: "remove" }] });

  const addNode = () => {
    const id = addForm.id.trim();
    if (!id) return;
    api
      .config({
        backends: [
          {
            id,
            action: "add",
            weight: addForm.weight,
            baseLatencyMs: addForm.latency,
            errorRatePct: addForm.error,
            capacity: addForm.capacity,
          },
        ],
      })
      .then(() => setShowAdd(false))
      .catch((e) => console.error(e));
  };

  const totalInFlight = useMemo(
    () => nodes.reduce((acc, n) => acc + n.inFlight, 0),
    [nodes],
  );

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-sm font-semibold text-slate-200">
          Backends <span className="text-slate-500 font-normal">({nodes.length})</span>
        </h2>
        <button
          onClick={() => setShowAdd((v) => !v)}
          className="flex items-center gap-1.5 rounded-lg px-2.5 py-1.5 text-xs font-medium bg-sky-600/20 text-sky-300 border border-sky-600/40 hover:bg-sky-600/30 transition"
        >
          <Plus size={13} /> Add backend
        </button>
      </div>

      {showAdd && (
        <div className="mb-4 grid grid-cols-2 md:grid-cols-6 gap-2 p-3 rounded-xl bg-slate-800/40 border border-slate-700">
          <input
            placeholder="id (e.g. node-d)"
            value={addForm.id}
            onChange={(e) => setAddForm((f) => ({ ...f, id: e.target.value }))}
            className="col-span-2 rounded-lg bg-slate-800 border border-slate-700 px-2 py-1.5 text-sm"
          />
          {(
            [
              ["weight", "Weight", addForm.weight, (v: number) => setAddForm((f) => ({ ...f, weight: v }))],
              ["latency", "Latency", addForm.latency, (v: number) => setAddForm((f) => ({ ...f, latency: v }))],
              ["error", "Err %", addForm.error, (v: number) => setAddForm((f) => ({ ...f, error: v }))],
              ["capacity", "Capacity", addForm.capacity, (v: number) => setAddForm((f) => ({ ...f, capacity: v }))],
            ] as const
          ).map(([key, label, value, set]) => (
            <div key={key}>
              <label className="block text-[10px] uppercase text-slate-500 mb-0.5">{label}</label>
              <input
                type="number"
                value={value}
                onChange={(e) => set(Number(e.target.value))}
                className="w-full rounded-lg bg-slate-800 border border-slate-700 px-2 py-1.5 text-sm"
              />
            </div>
          ))}
          <button
            onClick={addNode}
            className="self-end rounded-lg bg-sky-600 text-white px-2 py-1.5 text-sm font-medium hover:bg-sky-500"
          >
            Create
          </button>
        </div>
      )}

      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs text-slate-500 uppercase tracking-wider">
              <th className="py-2 pr-4">Node</th>
              <th className="py-2 pr-4">State</th>
              <th className="py-2 pr-4">Weight</th>
              <th className="py-2 pr-4">Latency</th>
              <th className="py-2 pr-4">Err %</th>
              <th className="py-2 pr-4">Capacity</th>
              <th className="py-2 pr-4 text-right">In-flight</th>
              <th className="py-2 pr-4 text-right">EWMA</th>
              <th className="py-2 pr-4 text-right">Total</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {nodes.map((n) => {
              const d = draftOf(n);
              const color = nodeColor(n.state);
              return (
                <tr key={n.id} className="border-t border-slate-800/70">
                  <td className="py-2.5 pr-4">
                    <div className="flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full" style={{ background: color }} />
                      <span className="font-medium text-slate-200">{n.id}</span>
                      {n.inFlight >= n.capacity && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-500/15 text-amber-300">
                          saturated
                        </span>
                      )}
                    </div>
                  </td>
                  <td className="py-2.5 pr-4">
                    <span
                      className="px-2 py-0.5 rounded-full text-[11px] font-medium"
                      style={{ background: `${color}20`, color }}
                    >
                      {stateLabel[n.state]}
                    </span>
                  </td>
                  {(
                    [
                      ["weight", d.weight, 1, 10, (v: number) => commit(n, { weight: v })],
                      ["latency", d.latency, 0, 300, (v: number) => commit(n, { latency: v })],
                      ["error", d.error, 0, 30, (v: number) => commit(n, { error: v })],
                      ["capacity", d.capacity, 1, 60, (v: number) => commit(n, { capacity: v })],
                    ] as const
                  ).map(([key, value, min, max, set], i) => (
                    <td key={key} className={`py-2.5 pr-4 min-w-28 ${i === 0 ? "min-w-32" : ""}`}>
                      <div className="flex items-center gap-2">
                        <input
                          type="range"
                          min={min}
                          max={max}
                          value={value}
                          onChange={(e) => set(Number(e.target.value))}
                          className="w-full accent-sky-500"
                        />
                        <span className="w-10 text-right tabular-nums text-xs text-slate-300">
                          {value % 1 === 0 ? value : value.toFixed(1)}
                        </span>
                      </div>
                    </td>
                  ))}
                  <td className="py-2.5 pr-4 text-right tabular-nums text-slate-300">
                    {n.inFlight}
                  </td>
                  <td className="py-2.5 pr-4 text-right tabular-nums text-slate-300">
                    {Math.round(n.ewmaLatencyMs)}
                  </td>
                  <td className="py-2.5 pr-4 text-right tabular-nums text-slate-300">
                    {fmtNum(n.totalRequests)}
                  </td>
                  <td className="py-2.5 whitespace-nowrap">
                    <div className="flex items-center gap-1">
                      <button
                        title={n.state === "DRAINING" ? "Resume" : "Pause"}
                        onClick={() => togglePause(n)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-amber-300 hover:bg-slate-800 transition"
                      >
                        {n.state === "DRAINING" ? <Play size={14} /> : <Pause size={14} />}
                      </button>
                      <button
                        title={n.state === "DOWN" ? "Revive" : "Mark down"}
                        onClick={() => toggleHealth(n)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-rose-300 hover:bg-slate-800 transition"
                      >
                        {n.state === "DOWN" ? <HeartPulse size={14} /> : <Skull size={14} />}
                      </button>
                      <button
                        title="Remove"
                        onClick={() => remove(n)}
                        className="p-1.5 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition"
                      >
                        <Trash2 size={14} />
                      </button>
                    </div>
                  </td>
                </tr>
              );
            })}
            {nodes.length === 0 && (
              <tr>
                <td colSpan={10} className="py-8 text-center text-slate-500">
                  No backends. Add one to start routing traffic.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <p className="mt-3 text-xs text-slate-500">
        Total in-flight: <span className="tabular-nums text-slate-300">{totalInFlight}</span> · Changes
        apply instantly. Use Pause/Kill to watch the health checker and draining react.
      </p>
    </section>
  );
}