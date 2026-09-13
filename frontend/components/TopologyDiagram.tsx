"use client";

import { useEffect, useRef, useState } from "react";
import { useLiveNodes, useLiveRequests, useLiveSnapshot } from "@/lib/store";
import { CLIENT_COLORS, nodeColor } from "@/lib/algorithms";
import type { NodeInfo, RequestEvent } from "@/lib/types";

const W = 1024;
const H = 320;
const CLIENT_X = 30;
const LB_X = 380;
const LB_W = 180;
const LB_H = 72;
const LB_CY = H / 2;
const NODE_X = 720;
const NODE_W = 170;
const NODE_H = 58;

interface Pulse {
  key: string;
  at: number;
  d: string;
  color: string;
  fail?: boolean;
}

function hashStr(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0;
  return h;
}

export default function TopologyDiagram() {
  const snapshot = useLiveSnapshot();
  const requests = useLiveRequests();
  const [pulses, setPulses] = useState<Pulse[]>([]);
  const counter = useRef(0);

  const nodes = useLiveNodes();
  const clientsShown = Math.min(snapshot?.load.numClients ?? 6, 10);

  const clientY = (i: number) => 28 + i * 24;
  const nodePos = (idx: number, total: number) => {
    const step = Math.min(72, (H - 48) / Math.max(total, 1));
    const y = 24 + idx * step;
    return { y, cy: y + NODE_H / 2 };
  };

  useEffect(() => {
    if (requests.length === 0) return;
    const recent = requests.slice(-8);
    const newPulses: Pulse[] = recent.map((r: RequestEvent) => {
      const idx = Math.abs(hashStr(r.client ?? "x")) % Math.max(clientsShown, 1);
      const nodeIdx = nodes.findIndex((n) => n.id === r.node);
      const cY = clientY(idx);
      const part2 = nodeIdx >= 0 ? ` L ${NODE_X} ${nodePos(nodeIdx, nodes.length).cy}` : "";
      return {
        key: `${r.id}-${counter.current++}`,
        at: Date.now(),
        d: `M ${CLIENT_X - 4} ${cY} L ${LB_X} ${LB_CY}${part2}`,
        color: CLIENT_COLORS[idx % CLIENT_COLORS.length],
        fail: r.status >= 400,
      };
    });
    setPulses((p) => [...p, ...newPulses].slice(-24));
  }, [requests, clientsShown, nodes]);

  useEffect(() => {
    if (pulses.length === 0) return;
    const t = setTimeout(() => {
      setPulses((p) => p.filter((x) => Date.now() - x.at < 900));
    }, 250);
    return () => clearTimeout(t);
  }, [pulses]);

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
      <div className="flex items-center justify-between mb-2">
        <h2 className="text-sm font-semibold text-slate-200">Topology</h2>
        <div className="flex items-center gap-4 text-[11px] text-slate-400">
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-sky-400 inline-block" /> clients
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-indigo-400 inline-block" /> load balancer
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-400 inline-block" /> backends
          </span>
        </div>
      </div>

      <div className="overflow-x-auto">
        <svg viewBox={`0 0 ${W} ${H}`} className="min-w-[720px] w-full" role="img" aria-label="Load balancer topology diagram">
          <defs>
            <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#334155" />
            </marker>
          </defs>

          {/* connection lines LB -> nodes */}
          {nodes.map((n, i) => {
            const p = nodePos(i, nodes.length);
            return (
              <line
                key={`ln-${n.id}`}
                x1={LB_X + LB_W}
                y1={LB_CY}
                x2={NODE_X}
                y2={p.cy}
                stroke="#334155"
                strokeWidth={1.5}
                markerEnd="url(#arrow)"
                strokeDasharray="4 4"
                strokeOpacity={n.state === "DOWN" ? 0.25 : 0.8}
              />
            );
          })}

          {/* client circles */}
          {Array.from({ length: clientsShown }, (_, i) => {
            const color = CLIENT_COLORS[i % CLIENT_COLORS.length];
            return (
              <g key={i}>
                <circle cx={CLIENT_X} cy={clientY(i)} r={7} fill={color} opacity={0.9} />
                <text x={CLIENT_X + 14} y={clientY(i) + 3} fontSize={9} fill="#64748b">
                  client-{i}
                </text>
              </g>
            );
          })}

          {/* LB box */}
          <rect x={LB_X} y={LB_CY - LB_H / 2} width={LB_W} height={LB_H} rx={14} fill="#141e33" stroke="#6366f1" strokeWidth={1.5} />
          <text x={LB_X + LB_W / 2} y={LB_CY - 6} textAnchor="middle" fontSize={12} fill="#c7d2fe" fontWeight={600} letterSpacing="0.5">
            LOAD BALANCER
          </text>
          <text x={LB_X + LB_W / 2} y={LB_CY + 14} textAnchor="middle" fontSize={11} fill="#94a3b8">
            {(snapshot?.algorithm ?? "round-robin").replace(/-/g, " ")}
          </text>

          {/* backend nodes */}
          {nodes.map((n, i) => {
            const p = nodePos(i, nodes.length);
            const color = nodeColor(n.state);
            const dim = n.state === "DOWN";
            return (
              <g key={`node-${n.id}`} opacity={dim ? 0.45 : 1}>
                <rect x={NODE_X} y={p.y} width={NODE_W} height={NODE_H} rx={12} fill="#0d1526" stroke={color} strokeWidth={1.5} />
                <circle cx={NODE_X + 16} cy={p.y + 18} r={5} fill={color} />
                <text x={NODE_X + 28} y={p.y + 22} fontSize={12} fill="#e2e8f0" fontWeight={600}>
                  {n.id}
                </text>
                <text x={NODE_X + NODE_W - 12} y={p.y + 18} fontSize={9} textAnchor="end" fill="#64748b">
                  {n.state.toLowerCase()}
                </text>
                <text x={NODE_X + 12} y={p.y + 42} fontSize={10} fill="#94a3b8">
                  in-flight {n.inFlight}/{n.capacity} · {Math.round(n.ewmaLatencyMs)}ms · {n.errorRatePct.toFixed(1)}% err
                </text>
              </g>
            );
          })}

          {/* animated request pulses */}
          {pulses.map((p) => (
            <circle key={p.key} r={4} className="req-pulse" fill={p.fail ? "#f87171" : p.color}>
              <animateMotion
                dur="0.7s"
                repeatCount="1"
                fill="freeze"
                path={p.d}
              />
            </circle>
          ))}
        </svg>
      </div>

      <p className="mt-2 text-xs text-slate-500">
        {snapshot && nodes.length > 0
          ? `Streaming ${snapshot.totals.reqRate} r/s across ${nodes.filter((n) => n.state === "HEALTHY").length} healthy backend(s).`
          : "Waiting for the live stream…"}
      </p>
    </section>
  );
}