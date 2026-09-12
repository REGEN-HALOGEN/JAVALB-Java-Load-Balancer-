import type { AlgorithmMeta } from "./types";

export const ALGORITHMS: AlgorithmMeta[] = [
  {
    name: "round-robin",
    title: "Round Robin",
    desc: "Rotates through the healthy backends in order, one request each.",
    formula: "n = (n + 1) mod size(nodes)",
  },
  {
    name: "weighted-round-robin",
    title: "Weighted Round Robin",
    desc: "Smooth (nginx-style) distribution proportional to each backend's weight, without bursts.",
    formula: "Cᵢ += Wᵢ · pick max(Cᵢ) · Cᵢ −= ΣW",
  },
  {
    name: "least-connections",
    title: "Least Connections",
    desc: "Sends each request to the backend with the fewest in-flight requests.",
    formula: "argmin(inFlight)",
  },
  {
    name: "least-response-time",
    title: "Least Response Time",
    desc: "Sends each request to the backend with the lowest EWMA-smoothed latency.",
    formula: "argmin(EWMA(latency))",
  },
  {
    name: "ip-hash",
    title: "IP Hash",
    desc: "Deterministic client → backend mapping via a consistent-hash ring (affinity without cookies).",
    formula: "ring.ceiling(hash(clientIp))",
  },
  {
    name: "random",
    title: "Random",
    desc: "Picks one of the healthy backends uniformly at random.",
    formula: "rand(0, size(nodes) − 1)",
  },
];

export const CLIENT_COLORS = [
  "#38bdf8",
  "#a78bfa",
  "#f472b6",
  "#34d399",
  "#fbbf24",
  "#fb7185",
  "#4ade80",
  "#e879f9",
  "#22d3ee",
  "#fdba74",
];

export const nodeColor = (state: string) =>
  state === "HEALTHY" ? "#34d399" : state === "DRAINING" ? "#fbbf24" : "#f87171";