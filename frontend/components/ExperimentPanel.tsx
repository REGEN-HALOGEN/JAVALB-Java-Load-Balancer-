"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { FlaskConical, RotateCw, Square } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { api } from "@/lib/api";
import { fetchExperiments, hasSupabase } from "@/lib/supabase";
import { fmtDuration, fmtMs, fmtNum, fmtPct } from "@/lib/format";
import type { Experiment } from "@/lib/types";

const tooltipStyle = {
  background: "#0f172a",
  border: "1px solid #334155",
  borderRadius: 8,
  fontSize: 11,
  color: "#e2e8f0",
} as const;

const axisTick = { fontSize: 9, fill: "#64748b" } as const;

export default function ExperimentPanel() {
  const [name, setName] = useState("baseline");
  const [active, setActive] = useState<string | null>(null);
  const [exps, setExps] = useState<Experiment[]>([]);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(async () => {
    setExps(await fetchExperiments(30));
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const start = async () => {
    setBusy(true);
    try {
      const res = await api.startExperiment(name.trim() || "experiment");
      if (res.started) setActive(res.experimentId);
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(false);
    }
  };

  const stop = async () => {
    setBusy(true);
    try {
      await api.stopExperiment();
      setActive(null);
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(false);
      refresh();
    }
  };

  // aggregate finished runs per algorithm for the comparison charts
  const compare = useMemo(() => {
    const byAlgo = new Map<
      string,
      { algorithm: string; latency: number; errors: number; n: number; totalReq: number }
    >();
    for (const e of exps) {
      if (e.avg_latency_ms == null || e.error_rate == null) continue;
      const key = e.algorithm ?? "unknown";
      const acc = byAlgo.get(key) ?? { algorithm: key, latency: 0, errors: 0, n: 0, totalReq: 0 };
      acc.latency += e.avg_latency_ms;
      acc.errors += e.error_rate;
      acc.n += 1;
      acc.totalReq += e.total_requests ?? 0;
      byAlgo.set(key, acc);
    }
    return Array.from(byAlgo.values()).map((a) => ({
      algorithm: a.algorithm,
      nm: a.algorithm.replace(/_/g, " ").replace(/-/g, " "),
      avgLatency: Math.round(a.latency / a.n),
      avgError: Number((a.errors / a.n).toFixed(2)),
      runs: a.n,
    }));
  }, [exps]);

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-200">
          <FlaskConical size={15} className="text-violet-400" />
          Experiments
        </h2>
        <span className="flex items-center gap-2 text-[11px]">
          <span
            className={`w-2 h-2 rounded-full ${hasSupabase ? "bg-emerald-400" : "bg-slate-600"}`}
          />
          <span className="text-slate-400">
            {hasSupabase ? "persisted to Supabase" : "local only (no Supabase URL)"}
          </span>
        </span>
      </div>

      <div className="flex flex-wrap items-center gap-2 mb-4">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          disabled={active != null}
          placeholder="experiment name"
          className="flex-1 min-w-40 rounded-lg bg-slate-800 border border-slate-700 px-3 py-2 text-sm focus:border-violet-500 outline-none"
        />
        {active == null ? (
          <button
            onClick={start}
            disabled={busy}
            className="flex items-center gap-2 rounded-lg bg-violet-600 px-4 py-2 text-sm font-medium hover:bg-violet-500 transition disabled:opacity-50"
          >
            <FlaskConical size={14} /> Start run
          </button>
        ) : (
          <button
            onClick={stop}
            disabled={busy}
            className="flex items-center gap-2 rounded-lg bg-amber-600/20 text-amber-300 border border-amber-600/40 px-4 py-2 text-sm font-medium hover:bg-amber-600/30 transition"
          >
            <Square size={14} /> Stop &amp; record
          </button>
        )}
        <button
          onClick={refresh}
          className="p-2 rounded-lg text-slate-400 hover:text-slate-200 hover:bg-slate-800 transition"
          title="Refresh history"
        >
          <RotateCw size={15} />
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-4">
        <div className="rounded-xl border border-slate-800 bg-slate-800/30 p-3">
          <h3 className="text-[11px] uppercase tracking-wide text-slate-400 mb-2">
            Avg latency by algorithm
          </h3>
          {compare.length > 0 ? (
            <ResponsiveContainer width="100%" height={140}>
              <BarChart data={compare} margin={{ top: 4, right: 4, left: -22, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="nm" tick={axisTick} interval={0} />
                <YAxis tick={axisTick} />
                <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "#1e293b55" }} />
                <Bar dataKey="avgLatency" name="ms" radius={[5, 5, 0, 0]}>
                  {compare.map((c) => (
                    <Cell key={c.algorithm} fill="#a78bfa" />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="grid place-items-center h-[140px] text-xs text-slate-500">
              Finish a run to compare algorithms
            </div>
          )}
        </div>
        <div className="rounded-xl border border-slate-800 bg-slate-800/30 p-3">
          <h3 className="text-[11px] uppercase tracking-wide text-slate-400 mb-2">
            Error rate by algorithm (%)
          </h3>
          {compare.length > 0 ? (
            <ResponsiveContainer width="100%" height={140}>
              <BarChart data={compare} margin={{ top: 4, right: 4, left: -22, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="nm" tick={axisTick} interval={0} />
                <YAxis tick={axisTick} />
                <Tooltip contentStyle={tooltipStyle} cursor={{ fill: "#1e293b55" }} />
                <Bar dataKey="avgError" name="%" radius={[5, 5, 0, 0]}>
                  {compare.map((c) => (
                    <Cell key={c.algorithm} fill="#fb7185" />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          ) : (
            <div className="grid place-items-center h-[140px] text-xs text-slate-500">
              Finish a run to compare algorithms
            </div>
          )}
        </div>
      </div>

      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="text-left text-slate-500 uppercase tracking-wider border-b border-slate-800">
              <th className="py-2 pr-3">Experiment</th>
              <th className="py-2 pr-3">Algorithm</th>
              <th className="py-2 pr-3 text-right">Duration</th>
              <th className="py-2 pr-3 text-right">Requests</th>
              <th className="py-2 pr-3 text-right">Avg</th>
              <th className="py-2 pr-3 text-right">p95</th>
              <th className="py-2 text-right">Error</th>
            </tr>
          </thead>
          <tbody>
            {exps.slice(0, 10).map((e) => (
              <tr key={e.id} className="border-b border-slate-800/50">
                <td className="py-2 pr-3 text-slate-200 font-medium">{(e.name ?? "unnamed").slice(0, 28)}</td>
                <td className="py-2 pr-3 capitalize text-slate-400">
                  {(e.algorithm ?? "—").replace(/-/g, " ")}
                </td>
                <td className="py-2 pr-3 text-right tabular-nums text-slate-400">
                  {fmtDuration(e.duration_ms)}
                </td>
                <td className="py-2 pr-3 text-right tabular-nums text-slate-300">
                  {fmtNum(e.total_requests)}
                </td>
                <td className="py-2 pr-3 text-right tabular-nums text-slate-300">
                  {fmtMs(e.avg_latency_ms)}
                </td>
                <td className="py-2 pr-3 text-right tabular-nums text-slate-300">
                  {fmtMs(e.p95_latency_ms)}
                </td>
                <td className="py-2 text-right tabular-nums text-slate-300">
                  {fmtPct(e.error_rate)}
                </td>
              </tr>
            ))}
            {exps.length === 0 && (
              <tr>
                <td colSpan={7} className="py-6 text-center text-slate-500">
                  No experiments recorded yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}