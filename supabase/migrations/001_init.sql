-- JavaLB: experiment history & saved configuration snapshots
-- Run against your Supabase project (SQL Editor or supabase db push).

create extension if not exists pgcrypto;

-- One row per experiment run (an algorithm under a specific load/config).
create table if not exists experiments (
  id              uuid primary key default gen_random_uuid(),
  name            text not null,
  algorithm       text not null,
  config          jsonb not null default '{}'::jsonb,
  started_at      timestamptz not null default now(),
  ended_at        timestamptz,
  duration_ms     bigint,
  total_requests  bigint,
  avg_latency_ms  double precision,
  p95_latency_ms  double precision,
  error_rate      double precision,
  per_node        jsonb
);

-- Per-experiment time series sampled every ~2s during a live run,
-- so finished runs can be replayed as charts.
create table if not exists experiment_points (
  id             bigint generated always as identity primary key,
  experiment_id  uuid not null references experiments(id) on delete cascade,
  ts             timestamptz not null default now(),
  req_rate       double precision not null default 0,
  total_p95_ms   double precision,
  latency_p50_ms double precision,
  latency_p99_ms double precision,
  per_node       jsonb
);

-- Saved dashboard/load-balancer configuration snapshots.
create table if not exists saved_configs (
  id         uuid primary key default gen_random_uuid(),
  name       text not null,
  config     jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists experiment_points_experiment_id_idx
  on experiment_points (experiment_id, ts);

-- ---------------------------------------------------------------
-- DEMO-ONLY policies (no auth). Anyone with the project anon key can
-- read/write. Fine for a showcase; lock these down before exposing to
-- the public internet.
-- ---------------------------------------------------------------
alter table experiments      enable row level security;
alter table experiment_points enable row level security;
alter table saved_configs     enable row level security;

create policy "anon read experiments"      on experiments      for select using (true);
create policy "anon insert experiments"    on experiments      for insert with check (true);
create policy "anon update experiments"    on experiments      for update using (true);
create policy "anon read points"           on experiment_points for select using (true);
create policy "anon insert points"         on experiment_points for insert with check (true);
create policy "anon read configs"          on saved_configs     for select using (true);
create policy "anon insert configs"        on saved_configs     for insert with check (true);