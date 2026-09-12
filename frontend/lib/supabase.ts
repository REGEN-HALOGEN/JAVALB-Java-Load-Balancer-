import { createClient } from "@supabase/supabase-js";
import type { Experiment } from "./types";

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
const supabaseAnon = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;

export const supabase =
  supabaseUrl && supabaseAnon ? createClient(supabaseUrl, supabaseAnon) : null;

/** True when Supabase env vars are configured (dashboard still works without). */
export const hasSupabase = supabase !== null;

const FIELDS =
  "id,name,algorithm,started_at,ended_at,duration_ms,total_requests,avg_latency_ms,p95_latency_ms,error_rate,per_node";

/** Loads experiment history from Supabase; falls back to the Java REST endpoint. */
export async function fetchExperiments(limit = 50): Promise<Experiment[]> {
  if (supabase) {
    const { data, error } = await supabase
      .from("experiments")
      .select(FIELDS)
      .order("started_at", { ascending: false })
      .limit(limit);
    if (!error && Array.isArray(data)) {
      return data as Experiment[];
    }
    if (error) console.warn("[supabase] fetch experiments:", error.message);
  }
  // fallback: the Java API mirrors identical rows
  const { api } = await import("./api");
  return api.experiments().catch(() => []);
}