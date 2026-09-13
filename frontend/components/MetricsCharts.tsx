"use client";

import type { ReactNode } from "react";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useLiveNodes, useLiveSnapshot } from "@/lib/store";
import { nodeColor } from "@/lib/algorithms";
import { fmtMs, fmtNum, fmtPct, fmtRate } from "@/lib/format";

const tooltipStyle = {
  background: "#0f172a",
  border: "1px solid #334155",
  borderRadius: 8,
  fontSize: 11,
  color: "#e2e8f0",
} as const;

const axisTick = { fontSize: 9, fill: "#64748b" } as const;

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="rounded-xl border border-slate-800 bg-slate-800/30 p-3">
      <h3 className="text-xs font-semibold text-slate-300 mb-2 uppercase tracking-wide">{title}</h3>
      {children}
    </div>
  );
}

function Empty() {
  return (
    <div className="grid place-items-center h-[170px] text-xs text-slate-500">
      Generating traffic…
    </div>
  );
}

function RatesChart() {
  const snapshot = useLiveSnapshot();
  const totals = snapshot?.totals;
  const series = totals?.rateSeries ?? [];
  const data = series.map((rate, i) => ({ t: `${-(series.length - 1 - i)}s`, rate }));

  return (
    <ChartCard title="Request rate (r/s)">
      {totals && totals.totalRequests > 0 ? (
        <ResponsiveContainer width="100%" height={170}>
          <AreaChart data={data} margin={{ top: 6, right: 6, left: -20, bottom: 0 }}>
            <defs>
              <linearGradient id="rateFill" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="#38bdf8" stopOpacity={0.6} />
                <stop offset="100%" stopColor="#38bdf8" stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="t" tick={axisTick} minTickGap={40} />
            <YAxis tick={axisTick} />
            <Tooltip contentStyle={tooltipStyle} />
            <Legend wrapperStyle={{ fontSize: 11, color: "#94a3b8" }} />
            <Area type="monotone" dataKey="rate" name="r/s" stroke="#38bdf8" fill="url(#rateFill)" isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
      ) : (
        <Empty />
      )}
    </ChartCard>
  );
}

function LatencyChart() {
  const snapshot = useLiveSnapshot();
  const totals = snapshot?.totals;
  const series = totals?.p95Series ?? [];
  const data = series.map((p95, i) => ({
    t: `${-(series.length - 1 - i)}s`,
    p95,
    p50: totals?.p50Series?.[i] ?? 0,
    p99: totals?.p99Series?.[i] ?? 0,
  }));

  return (
    <ChartCard title="Latency (p50 / p95 / p99)">
      {totals && totals.totalRequests > 0 ? (
        <ResponsiveContainer width="100%" height={170}>
          <LineChart data={data} margin={{ top: 6, right: 6, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="t" tick={axisTick} minTickGap={40} />
            <YAxis tick={axisTick} />
            <Tooltip contentStyle={tooltipStyle} />
            <Legend wrapperStyle={{ fontSize: 11, color: "#94a3b8" }} />
            <Line type="monotone" dataKey="p50" name="p50 ms" stroke="#34d399" dot={false} strokeWidth={1.5} />
            <Line type="monotone" dataKey="p95" name="p95 ms" stroke="#fbbf24" dot={false} strokeWidth={1.5} />
            <Line type="monotone" dataKey="p99" name="p99 ms" stroke="#f87171" dot={false} strokeWidth={1.5} />
          </LineChart>
        </ResponsiveContainer>
      ) : (
        <Empty />
      )}
    </ChartCard>
  );
}

function PerNodeChart() {
  const nodes = useLiveNodes();
  const data = nodes.map((n) => ({ name: n.id, requests: n.totalRequests, state: n.state, inFlight: n.inFlight }));

  return (
    <ChartCard title="Requests per backend">
      {data.length > 0 ? (
        <ResponsiveContainer width="100%" height={170}>
          <BarChart data={data} margin={{ top: 6, right: 6, left: -20, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="name" tick={axisTick} />
            <YAxis tick={axisTick} />
            <Tooltip contentStyle={tooltipStyle} />
            <Bar dataKey="requests" name="processed" radius={[6, 6, 0, 0]}>
              {data.map((d) => (
                <Cell key={d.name} fill={nodeColor(d.state)} fillOpacity={d.state === "DOWN" ? 0.35 : 0.9} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      ) : (
        <Empty />
      )}
    </ChartCard>
  );
}

export default function MetricsCharts() {
  const snapshot = useLiveSnapshot();
  const totals = snapshot?.totals;
  const nodes = useLiveNodes();
  const stats = [
    { label: "Total requests", value: fmtNum(totals?.totalRequests) },
    { label: "Current rate", value: fmtRate(totals?.reqRate) },
    { label: "Avg latency", value: fmtMs(totals?.avgLatencyMs) },
    { label: "p95 latency", value: fmtMs(totals?.p95LatencyMs) },
    { label: "Error rate", value: fmtPct(totals?.failureRatePct) },
    { label: "In-flight", value: fmtNum(nodes.reduce((a, n) => a + n.inFlight, 0)) },
    { label: "Sticky sessions", value: fmtNum(snapshot?.stickyMappings) },
  ];

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
      <h2 className="text-sm font-semibold text-slate-200 mb-4">Metrics</h2>
      <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-2 mb-4">
        {stats.map((s) => (
          <div key={s.label} className="rounded-xl bg-slate-800/40 border border-slate-800 px-3 py-2">
            <p className="text-[10px] uppercase tracking-wide text-slate-500">{s.label}</p>
            <p className="mt-1 text-sm font-semibold tabular-nums text-slate-100">{s.value}</p>
          </div>
        ))}
      </div>
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        <RatesChart />
        <LatencyChart />
        <PerNodeChart />
      </div>
    </section>
  );
}