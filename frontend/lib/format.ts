export const fmtNum = (v: number | null | undefined): string =>
  v == null || Number.isNaN(v) ? "—" : Math.round(v).toLocaleString();

export const fmtMs = (v: number | null | undefined): string =>
  v == null || Number.isNaN(v) ? "—" : `${v % 1 === 0 ? v : Number(v).toFixed(1)} ms`;

export const fmtRate = (v: number | null | undefined): string =>
  v == null || Number.isNaN(v) ? "—" : `${Math.round(v).toLocaleString()} r/s`;

export const fmtPct = (v: number | null | undefined): string =>
  v == null || Number.isNaN(v) ? "—" : `${Number(v).toFixed(v >= 10 ? 1 : 2)}%`;

export const fmtDuration = (ms: number | null | undefined): string => {
  if (ms == null || Number.isNaN(ms)) return "—";
  if (ms < 1000) return `${ms} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toFixed(1)} s`;
  return `${Math.floor(s / 60)}m ${Math.round(s % 60)}s`;
};

export const fmtTime = (ts: number): string =>
  new Date(ts).toLocaleTimeString([], { hour12: false });

/** Sequence for Recharts x-axis labels shared across charts. */
export const seqLabels = (n: number): string[] =>
  Array.from({ length: n }, (_, i) => `${-n + 1 + i}s`);