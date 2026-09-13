# JavaLB Handoff Document

## Project Overview
**JavaLB** — Interactive Java Load Balancer Showcase
- **Backend**: Java 21 + Javalin 6 (Gradle) — runs on Railway/Render
- **Frontend**: Next.js 15 + Tailwind + Recharts + Zustand — deploys to Vercel
- **Database**: Supabase (Postgres) — optional, for experiment persistence
- **Repo**: https://github.com/REGEN-HALOGEN/JAVALB-Java-Load-Balancer-

## Current State (v0.1.0 - 2026-09-12)

### ✅ Completed
- **Backend core** (`backend/`):
  - 6 load balancing strategies: round-robin, weighted round-robin, least-connections, least-response-time (EWMA), IP hash (consistent hash ring), random
  - In-process mock HTTP backends (JDK `HttpServer`) with configurable latency/error/capacity
  - Health checking: active `/healthz` probes + passive failure counting
  - State machine: `HEALTHY` ↔ `DRAINING` (pause/resume) ↔ `DOWN` (kill/revive)
  - Sticky sessions with client→backend pinning
  - Metrics: counters, 300s ring buffer, latency reservoir (p50/p95/p99)
  - Load generator: steady/sine/burst/spike patterns, configurable rate/concurrency/clients
  - Supabase persistence (optional): experiments + time-series points
  - REST API (`/api/*`) + WebSocket live stream (`/ws/events`)
  - Unit tests: strategy distributions, health transitions, engine routing

- **Frontend dashboard** (`frontend/`):
  - Topology diagram (animated SVG request pulses)
  - Algorithm picker with formula tooltips
  - Backend table (live sliders for weight/latency/error/capacity + pause/kill/revive/remove)
  - Load generator panel
  - Metrics charts (rate, latency percentiles, per-backend bars)
  - Request log (last 150 routing decisions)
  - Experiment panel with history + algorithm comparison charts

- **Deploy configs**: Dockerfile, railway.toml, render.yaml, Vercel-ready
- **Supabase migration**: `supabase/migrations/001_init.sql`
- **README**: Architecture, deploy steps, API reference, changelog

### 🔄 Verified Working
- Backend: `./gradlew build fatJar` → `java -jar build/libs/*-all.jar`
- Frontend: `npm run build` + `npm run start` (port 3000)
- Live WebSocket: snapshots every 500ms + per-request events
- End-to-end: start load generator → switch algorithms → kill nodes → watch topology react

## Architecture

```
 Browser (Vercel/Next.js)                          Other callers
        │  REST (control)                    │
        │  WebSocket (live stats)            │
        ▼                                    ▼
┌─────────────────────────────────────────────────────┐
│ Java Load Balancer (Javalin) — Railway/Render       │
│  strategies · health check · sticky sessions        │
│  load generator · metrics · request log             │
└────────┬──────────────────────────────┬────────────┘
         │ forwards proxied traffic     │ /healthz probes
         ▼                              ▼
┌──────────────────────────┐    in-process mock backends
│ Mock backends: N in-     │    (JDK HttpServer, random ports)
│ memory HTTP servers      │    configurable latency/error/capacity
└──────────────────────────┘
         │  synch experiments & config snapshots
         ▼
┌──────────────────────────┐
│ Supabase (Postgres)      │  experiments, experiment_points, saved_configs
└──────────────────────────┘
```

## Key Files

### Backend
| File | Purpose |
|------|---------|
| `backend/src/main/java/.../App.java` | Entry point, routes, WS hub, experiment lifecycle |
| `.../core/LoadBalancerEngine.java` | Routing core, strategy selection, stats, event emission |
| `.../core/BackendNode.java` | Node config + live stats + state machine |
| `.../core/strategy/*.java` | 6 strategy implementations |
| `.../core/MockBackendServer.java` | In-process HTTP backend simulator |
| `.../core/HealthChecker.java` | Active probes + passive failures |
| `.../core/StickySessionManager.java` | Client→node affinity |
| `.../metrics/MetricsStore.java` | Ring buffers + latency reservoir |
| `.../traffic/LoadGenerator.java` | Patterned traffic generation |
| `.../persistence/SupabaseRepo.java` | Optional JDBC persistence |
| `.../api/WsHub.java` | WebSocket fan-out for live data |

### Frontend
| File | Purpose |
|------|---------|
| `frontend/app/page.tsx` | Main dashboard layout |
| `components/TopologyDiagram.tsx` | Animated request flow |
| `components/AlgorithmPicker.tsx` | 6 strategy cards |
| `components/BackendTable.tsx` | Node management + live sliders |
| `components/LoadPanel.tsx` | Traffic generator controls |
| `components/MetricsCharts.tsx` | Recharts: rate, latency, per-node |
| `components/RequestLog.tsx` | Recent routing decisions |
| `components/ExperimentPanel.tsx` | Run + history + compare |
| `lib/store.ts` | Zustand store with shallow selectors |
| `lib/live.ts` | WebSocket connection + auto-reconnect |
| `lib/api.ts` | REST client to backend |

### Deploy
- `backend/Dockerfile` — multi-stage (Gradle build → Temurin JRE)
- `backend/railway.toml` — Railway config
- `render.yaml` — Render free-tier web service
- `frontend/` — Vercel auto-detects Next.js

## API Reference

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/health` | GET | Health check |
| `/api/state` | GET | Full snapshot (nodes, totals, config, load) |
| `/api/config` | POST | Strategy, sticky, health interval, backend mutations |
| `/api/load` | POST | Start/stop load generator |
| `/api/proxy` | POST | One manual routed request |
| `/api/experiment` | POST | Start named experiment |
| `/api/experiment/stop` | POST | Stop & persist experiment |
| `/api/experiments` | GET | Recent runs (from Supabase or Java mirror) |
| `/api/reset-stats` | POST | Zero all counters |
| `/ws/events` | WS | `snapshot` (500ms) + `request` events |

## Environment Variables

### Backend
```bash
PORT=8080
CORS_ORIGINS=https://your-dashboard.vercel.app
SUPABASE_DB_URL=postgresql://...    # optional
```

### Frontend (Vercel)
```bash
NEXT_PUBLIC_LB_URL=https://your-lb.up.railway.app
NEXT_PUBLIC_SUPABASE_URL=https://xxx.supabase.co    # optional
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJ...                # optional
```

## Pending Tasks / Next Steps

### High Priority
- [ ] **Add integration tests**: Start backend + mock clients, verify strategy behaviors under load
- [ ] **Frontend E2E tests**: Playwright/Cypress for critical user flows
- [ ] **Accessibility audit**: Keyboard nav, ARIA labels, color contrast
- [ ] **Dark/light mode toggle**: Currently hardcoded dark

### Medium Priority
- [ ] **Saved configs UI**: Persist/load named backend topologies via `/api/saved-configs` (backend endpoint exists, UI missing)
- [ ] **Experiment replay**: Load a finished run's `experiment_points` and animate charts from stored time-series
- [ ] **Comparison deep-dive**: Side-by-side topology + metrics for two experiments
- [ ] **WebSocket connection state indicator**: Show reconnecting/backoff status in header
- [ ] **Keyboard shortcuts**: Space=fire request, R=reset, S=start/stop load

### Low Priority / Nice-to-Have
- [ ] **Algorithm visualizer**: Step-through animation of WRR / consistent-hash ring
- [ ] **Custom backend templates**: Save/load common topologies (e.g., "canary", "blue-green")
- [ ] **Export charts**: PNG/CSV download for experiment results
- [ ] **Mobile layout**: Dashboard currently desktop-first
- [ ] **Theme customization**: CSS variables for brand colors

### Technical Debt
- [ ] **Replace `SseHub` → `WsHub`** completely (done in code, remove any leftover SSE references)
- [ ] **Type-safe WebSocket messages**: Discriminated union types for `snapshot` | `request` | `config`
- [ ] **Request deduplication**: Throttle high-rate request events in `TopologyDiagram` (currently renders up to 24 concurrent pulses)
- [ ] **Backend graceful shutdown**: Drain in-flight requests before stopping mock servers
- [ ] **Health check config API**: Expose `FAILURE_THRESHOLD` / `SUCCESS_THRESHOLD` as configurable

## Running Locally

```bash
# Backend
cd backend
./gradlew fatJar
java -jar build/libs/javalb-backend-0.1.0-all.jar
# → http://localhost:8080/health
# → ws://localhost:8080/ws/events

# Frontend
cd frontend
cp .env.example .env.local
npm install
npm run dev
# → http://localhost:3000
```

## Common Commands

```bash
# Backend
cd backend
./gradlew test              # Run unit tests
./gradlew fatJar            # Build uber-jar
./gradlew clean build       # Clean build

# Frontend
cd frontend
npm run dev                 # Dev server (port 3000)
npm run build               # Production build
npm run start               # Serve production build
npm run lint                # ESLint
```

## Known Issues

1. **Render free tier spins down** after ~15 min idle — dashboard's WebSocket keeps it warm while open
2. **No authentication** on Supabase — RLS policies are permissive (demo-only)
3. **Java 25** used locally; Dockerfile uses Temurin 21 — ensure compatibility if upgrading
4. **Request log grows unbounded** in Zustand store (capped at 400 but could use LRU)
5. **Experiment history only shows 50** — pagination not implemented

## Contact / Context

- **Owner**: REGEN-HALOGEN
- **Branch**: `master` (default)
- **Last deploy**: Not yet deployed to Railway/Render/Vercel — ready for first deploy
- **Changelog**: See README.md `## Changelog` section

---

*Generated 2026-09-12 — Use this document to onboard new agents or resume work after context loss.*