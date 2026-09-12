"use client";

import { useEffect } from "react";
import { connectLiveStream } from "@/lib/live";
import StatusHeader from "@/components/StatusHeader";
import TopologyDiagram from "@/components/TopologyDiagram";
import AlgorithmPicker from "@/components/AlgorithmPicker";
import LoadPanel from "@/components/LoadPanel";
import BackendTable from "@/components/BackendTable";
import MetricsCharts from "@/components/MetricsCharts";
import RequestLog from "@/components/RequestLog";
import ExperimentPanel from "@/components/ExperimentPanel";

export default function Page() {
  useEffect(() => connectLiveStream(), []);

  return (
    <div className="min-h-screen">
      <StatusHeader />
      <main className="mx-auto max-w-7xl px-6 py-6 space-y-6">
        <TopologyDiagram />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <AlgorithmPicker />
          <LoadPanel />
        </div>
        <BackendTable />
        <MetricsCharts />
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <RequestLog />
          <ExperimentPanel />
        </div>
      </main>
      <footer className="px-6 py-6 text-center text-xs text-slate-600">
        JavaLB — a Java load balancer showcase · Java (Javalin) · Next.js · Supabase
      </footer>
    </div>
  );
}