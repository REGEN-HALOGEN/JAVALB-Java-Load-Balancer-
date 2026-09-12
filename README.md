# JavaLB — Java Load Balancer Showcase

An interactive load balancing demo. A **Java load balancer** (Javalin + Netty-adjacent stack, no
Spring) routes simulated traffic across in-process mock HTTP backends using six switchable
strategies, while a **Next.js dashboard** shows every aspect of load balancing live: request
topology, health-check state changes, per-backend metrics, sticky sessions, and load generation.
Experiment runs are persisted to **Supabase** for post-run comparison.

```
 Browser / Vercel (Next.js dashboard)
   │   REST (control plane)        │   WebSocket (live snapshots + request events)
   ▼                               ▼
┌──────────────────────────────────────────────────┐
│ Java Load Balancer (Javalin) — Railway / Render  │
│   strategies · health checks · sticky sessions   │
│   load generator · metrics · request log         │
└──────────┬───────────────────────────────┬───────┘
           │  proxied HTTP                 │ /healthz probes
           ▼                               ▼
   mock backends (in-process)     Supabase (Postgres)
   configurable latency/errors    experiment history
```

## Features

- **Six switchable algorithms** — round-robin, smooth weighted round-robin, least-connections,
  least-response-time (EWMA), client IP hash (consistent-hash ring), and random.
- **Health checking** — active `/healthz` probes plus passive failure counting from real traffic.
  Watch a backend drop out of the pool and come back, live.
- **Sticky sessions** — pin a client to a backend (toggleable), with visible client groups in the
  topology view.
- **Live topology diagram** — animated request pulses flowing client → load balancer → backend.
- **Traffic generator** — steady / sine / burst / spike patterns, configurable rate, concurrency,
  virtual clients and duration.
- **Metrics & charts** — request rate, p50/p95/p99 latency, error rate, in-flight connections,
  per-backend totals.
- **Experiments** — name a run, drive traffic, stop it, and compare algorithms by latency and
  error rate in the dashboard. Persisted to Supabase.
- **Backend control** — add/remove backends, tweak weight, latency, error rate and capacity live,
  or kill/pause nodes to see the balancer react.

## Repository layout

```
backend/     Java load balancer (Gradle, Javalin, JDK HTTP client)
frontend/    Next.js dashboard (Tailwind, Recharts, zustand, WebSocket)
supabase/    SQL migrations (experiments, experiment_points, saved_configs)
render.yaml  Render free-tier config for the backend
```

## Quickstart (local)

### Prerequisites

- Java 21+ (JDK)
- Node.js 20+

### 1. Run the load balancer

```bash
cd backend
./gradlew fatJar
java -jar build/libs/javalb-backend-0.1.0-all.jar
```

Listens on `http://localhost:8080`. Health check: `curl localhost:8080/health`.
Live stream: `localhost:8080/ws/events`. Control plane under `/api/*` (see below).

> No database is required for local dev. Set `SUPABASE_DB_URL` if you want experiments persisted.

### 2. Run the dashboard

```bash
cd frontend
cp .env.example .env.local   # set NEXT_PUBLIC_LB_URL=http://localhost:8080 (default)
npm install
npm run dev
```

Open `http://localhost:3000`. Start the traffic generator, switch algorithms, kill a node,
and watch the topology react.

## Deploying

### Backend — Railway (recommended) or Render

Ship the `backend/` service using the provided Dockerfile:

| Host  | Steps |
| ----- | ----- |
| Railway | Create a new service → choose **Dockerfile** → set the root directory to `backend`. Add `PORT=8080`. Deploy. |
| Render  | New **Web Service** → runtime **Docker** → path `backend/Dockerfile` (or use the included `render.yaml`). Free tier spins down after ~15 min idle — the dashboard's WebSocket keeps it warm while open. |

Required env vars:

```
PORT=8080
CORS_ORIGINS=https://your-dashboard.vercel.app
SUPABASE_DB_URL=postgresql://postgres.<proj>:<password>@aws-0-<region>.pooler.supabase.com:6543/postgres   # optional
```

Weights, latency, error rates and capacities default to three seed backends (`node-a`, `node-b`,
`node-c`) with different profiles so strategy differences are immediately visible.
Someone can just use the dashboard config API to change everything at runtime.

### Frontend — Vercel

1. Import the `frontend/` directory into Vercel (framework preset: Next.js).
2. Set env vars:
   - `NEXT_PUBLIC_LB_URL=https://your-lb.up.railway.app`
   - `NEXT_PUBLIC_SUPABASE_URL` / `NEXT_PUBLIC_SUPABASE_ANON_KEY` (optional)
3. Deploy. In the dashboard header you'll see the `live` indicator turn green once it connects.

### Database — Supabase

1. Create a project.
2. Run `supabase/migrations/001_init.sql` via the SQL editor.
3. Grab the Postgres connection string (`Settings → Database → Connection string`).
4. Put it in the backend's `SUPABASE_DB_URL`.

> Note: migrations enable **permissive RLS policies for the demo** (no auth). Lock these down
> before exposing anything publicly.

## Control plane API

| Endpoint | Purpose |
| -------- | ------- |
| `GET  /api/state` | Full snapshot: nodes, counters, config, load status |
| `POST /api/config` | Set strategy / sticky sessions / health interval; add-update-remove backends |
| `POST /api/load` | Start/stop the generator: `ratePerSec`, `concurrency`, `pattern`, `numClients`, `durationSec` |
| `POST /api/proxy` | One manual request through the balancer (returns routing decision) |
| `POST /api/experiment` / `.../stop` | Manage persisted experiment runs |
| `GET  /api/experiments` | Recent runs (mirrors Supabase rows when DB is absent) |
| `POST /api/reset-stats` | Zero all counters |
| `WS   /ws/events` | WebSocket: `snapshot` (every 500ms) + `request` events |

## How the balancing works

| Strategy | What it does |
| -------- | ------------ |
| Round Robin | Rotates requests through the healthy backends one by one. |
| Weighted Round Robin | Smooth (nginx-style) WRR `Cᵢ += Wᵢ`, pick max, `Cᵢ -= ΣW` — ratios hold long-term without bursts. |
| Least Connections | Sends each request to the backend with fewest in-flight requests. |
| Least Response Time | Sends each request to the backend with the lowest EWMA-smoothed latency. |
| IP Hash | Consistent-hash ring: a client always lands on the same backend until the set changes. |
| Random | Uniform random pick among healthy backends. |

Health state machine: `HEALTHY → DRAINING → HEALTHY` (admin pause/resume) and
`HEALTHY → DOWN → HEALTHY` (probes/passive failures, half-open recovery).

## Changelog

### v0.1.0 — 2026-09-12 (initial scaffold)

- Java load balancer core (Javalin): six balancing strategies, mock backend HTTP servers,
  active + passive health checking, sticky session manager, metrics with p50/p95/p99
  percentiles and per-second history.
- Traffic generator: steady, sine, burst and spike patterns with configurable rate, concurrency
  and virtual clients.
- REST control plane (`/api/*`) and live WebSocket stream (`/ws/events`).
- Optional Supabase persistence (experiments + time-series points), offline-safe.
- Next.js dashboard: topology animation, algorithm picker, backend controls, load generator
  panel, metrics charts (Recharts), request log, experiment history + comparison.
- Dockerfile + Railway/Render config for the backend; Vercel-deployable frontend.
- Unit tests for strategies (WRR distribution, affinities, EWMA), health-state transitions
  and end-to-end engine routing smoke tests.