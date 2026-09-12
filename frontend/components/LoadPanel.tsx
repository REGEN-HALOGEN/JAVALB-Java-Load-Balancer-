"use client";

import { useEffect, useRef, useState } from "react";
import { Crosshair, Play, RotateCcw, Square } from "lucide-react";
import { api } from "@/lib/api";
import { useLiveStore } from "@/lib/store";

const PATTERNS = [
  { value: "steady", label: "Steady" },
  { value: "sine", label: "Sine wave" },
  { value: "burst", label: "Bursts" },
  { value: "spike", label: "Spikes" },
];

export default function LoadPanel() {
  const running = useLiveStore((s) => s.snapshot?.load.running ?? false);
  const snapLoad = useLiveStore((s) => s.snapshot?.load);

  const [rate, setRate] = useState(snapLoad?.ratePerSec ?? 60);
  const [concurrency, setConcurrency] = useState(snapLoad?.concurrency ?? 24);
  const [pattern, setPattern] = useState(snapLoad?.pattern ?? "steady");
  const [clients, setClients] = useState(snapLoad?.numClients ?? 12);
  const [duration, setDuration] = useState("30");
  const [busy, setBusy] = useState(false);

  // Sync local controls from server, but not while the user is interacting.
  const [dirty, setDirty] = useState(false);
  const dirtyTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const commitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (dirty || !snapLoad) return;
    setRate(snapLoad.ratePerSec);
    setConcurrency(snapLoad.concurrency);
    setPattern(snapLoad.pattern);
    setClients(snapLoad.numClients);
  }, [snapLoad, dirty]);

  const touch = (fn?: () => void) => {
    setDirty(true);
    if (dirtyTimer.current) clearTimeout(dirtyTimer.current);
    dirtyTimer.current = setTimeout(() => setDirty(false), 900);
    if (commitTimer.current) clearTimeout(commitTimer.current);
    commitTimer.current = setTimeout(() => fn?.(), 250);
  };

  const apply = () => {
    return api.load({
      running,
      ratePerSec: rate,
      concurrency,
      pattern,
      numClients: clients,
      durationSec: duration ? Number(duration) : 0,
    });
  };

  const applyWithoutRunning = async () => {
    try {
      await api.load({
        running,
        ratePerSec: rate,
        concurrency,
        pattern,
        numClients: clients,
        durationSec: duration ? Number(duration) : 0,
      });
    } catch (e) {
      console.error(e);
    }
  };

  const toggleRunning = async () => {
    setBusy(true);
    try {
      await api.load({
        running: !running,
        ratePerSec: rate,
        concurrency,
        pattern,
        numClients: clients,
        durationSec: duration ? Number(duration) : 0,
      });
    } catch (e) {
      console.error(e);
    } finally {
      setBusy(false);
    }
  };

  const fireOne = async () => {
    try {
      await api.proxy({ clientId: "manual-client" });
    } catch (e) {
      console.error(e);
    }
  };

  const resetStats = async () => {
    try {
      await api.resetStats();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <section className="rounded-2xl border border-slate-800 bg-slate-900/60 p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-200">
          <Play size={15} className="text-emerald-400" />
          Traffic generator
        </h2>
        <span
          className={`px-2.5 py-1 rounded-full text-xs font-medium ${
            running ? "bg-emerald-500/15 text-emerald-300" : "bg-slate-800 text-slate-400"
          }`}
        >
          {running ? "running" : "stopped"}
        </span>
      </div>

      <div className="space-y-4 text-sm">
        <div>
          <label className="flex justify-between text-xs text-slate-400 mb-1">
            <span>Request rate</span>
            <span className="text-slate-200 font-medium tabular-nums">{rate} r/s</span>
          </label>
          <input
            type="range"
            min={5}
            max={500}
            step={5}
            value={rate}
            onChange={(e) => {
              setRate(Number(e.target.value));
              touch(applyWithoutRunning);
            }}
            className="w-full accent-emerald-500"
          />
        </div>

        <div>
          <label className="flex justify-between text-xs text-slate-400 mb-1">
            <span>Concurrency</span>
            <span className="text-slate-200 font-medium tabular-nums">{concurrency}</span>
          </label>
          <input
            type="range"
            min={1}
            max={100}
            step={1}
            value={concurrency}
            onChange={(e) => {
              setConcurrency(Number(e.target.value));
              touch(applyWithoutRunning);
            }}
            className="w-full accent-emerald-500"
          />
        </div>

        <div>
          <label className="flex justify-between text-xs text-slate-400 mb-1">
            <span>Virtual clients</span>
            <span className="text-slate-200 font-medium tabular-nums">{clients}</span>
          </label>
          <input
            type="range"
            min={1}
            max={40}
            step={1}
            value={clients}
            onChange={(e) => {
              setClients(Number(e.target.value));
              touch(applyWithoutRunning);
            }}
            className="w-full accent-emerald-500"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="text-xs text-slate-400 mb-1 block">Pattern</label>
            <select
              value={pattern}
              onChange={(e) => {
                setPattern(e.target.value);
                touch(applyWithoutRunning);
              }}
              className="w-full rounded-lg bg-slate-800 border border-slate-700 px-2 py-1.5 text-sm"
            >
              {PATTERNS.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="text-xs text-slate-400 mb-1 block">
              Duration (s, 0 = forever)
            </label>
            <input
              type="number"
              min={0}
              value={duration}
              onChange={(e) => setDuration(e.target.value)}
              className="w-full rounded-lg bg-slate-800 border border-slate-700 px-2 py-1.5 text-sm"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-2 pt-1">
          <button
            onClick={toggleRunning}
            disabled={busy}
            className={`flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition ${
              running
                ? "bg-rose-600/20 text-rose-300 border border-rose-600/40 hover:bg-rose-600/30"
                : "bg-emerald-600 text-white hover:bg-emerald-500"
            }`}
          >
            {running ? <Square size={14} /> : <Play size={14} />}
            {running ? "Stop" : "Start"}
          </button>
          <button
            onClick={fireOne}
            className="flex items-center justify-center gap-2 rounded-lg px-3 py-2 text-sm font-medium bg-slate-800 border border-slate-700 hover:border-slate-500 transition"
          >
            <Crosshair size={14} />
            Fire one request
          </button>
        </div>

        <button
          onClick={resetStats}
          className="flex items-center gap-2 text-xs text-slate-400 hover:text-slate-200 transition"
        >
          <RotateCcw size={12} />
          Reset counters
        </button>
      </div>
    </section>
  );
}