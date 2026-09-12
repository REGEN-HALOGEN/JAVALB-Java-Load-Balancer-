"use client";

import { useState } from "react";
import { Check, GitBranch } from "lucide-react";
import { ALGORITHMS } from "@/lib/algorithms";
import { api } from "@/lib/api";
import { useLiveStore } from "@/lib/store";

export default function AlgorithmPicker() {
  const active = useLiveStore((s) => s.snapshot?.algorithm);
  const [busy, setBusy] = useState(false);

  const select = async (name: string) => {
    if (name === active || busy) return;
    setBusy(true);
    try {
      await api.config({ strategy: name });
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
      <h2 className="mb-4 flex items-center gap-2 text-sm font-semibold text-slate-200">
        <GitBranch size={15} className="text-indigo-400" />
        Balancing algorithm
      </h2>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {ALGORITHMS.map((a) => {
          const isActive = active === a.name;
          return (
            <button
              key={a.name}
              onClick={() => select(a.name)}
              disabled={busy}
              className={`relative text-left rounded-xl border p-3 transition ${
                isActive
                  ? "border-indigo-400 bg-indigo-500/10 ring-1 ring-indigo-400/40"
                  : "border-slate-700 bg-slate-800/40 hover:border-slate-500"
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-medium text-sm">{a.title}</span>
                {isActive && <Check size={15} className="text-indigo-400" />}
              </div>
              <p className="mt-1 text-xs text-slate-400 leading-relaxed">{a.desc}</p>
              <code className="mt-2 block text-[11px] text-slate-500">{a.formula}</code>
            </button>
          );
        })}
      </div>
    </section>
  );
}