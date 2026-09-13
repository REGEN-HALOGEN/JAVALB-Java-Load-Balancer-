#!/usr/bin/env bash
# JavaLB — one-click local startup
# Usage: ./run-local.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"

BACKEND_JAR="$BACKEND_DIR/build/libs/javalb-backend-0.1.0-all.jar"
BACKEND_LOG="$ROOT_DIR/backend.log"
FRONTEND_LOG="$ROOT_DIR/frontend.log"

PID_FILE="$ROOT_DIR/.run-pids"

cleanup() {
  echo ""
  echo "🛑 Shutting down..."
  if [[ -f "$PID_FILE" ]]; then
    while read -r pid; do
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null || true
      fi
    done < "$PID_FILE"
    rm -f "$PID_FILE"
  fi
  # Fallback: kill any remaining java/node on our ports
  fuser -k 8080/tcp 3000/tcp 2>/dev/null || true
  echo "✅ Done."
}
trap cleanup EXIT INT TERM

echo "🚀 JavaLB — Starting local stack"
echo "────────────────────────────────────────"

# 1. Build backend if needed
if [[ ! -f "$BACKEND_JAR" ]]; then
  echo "📦 Building backend (first run)..."
  cd "$BACKEND_DIR"
  ./gradlew fatJar --no-daemon -q
  echo "✅ Backend built"
else
  echo "✅ Backend JAR found"
fi

# 2. Install frontend deps if needed
if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  echo "📦 Installing frontend dependencies..."
  cd "$FRONTEND_DIR"
  npm install --no-audit --no-fund --silent 2>&1 | tail -3
  echo "✅ Frontend deps installed"
else
  echo "✅ Frontend deps present"
fi

# 3. Start backend
echo "☕ Starting Java load balancer on :8080..."
cd "$BACKEND_DIR"
java -jar "$BACKEND_JAR" > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
echo "$BACKEND_PID" > "$PID_FILE"

# Wait for backend health
for i in {1..15}; do
  if curl -sf http://localhost:8080/health >/dev/null 2>&1; then
    echo "✅ Backend healthy (pid $BACKEND_PID)"
    break
  fi
  sleep 1
  if [[ $i -eq 15 ]]; then
    echo "❌ Backend failed to start. Check $BACKEND_LOG"
    tail -20 "$BACKEND_LOG"
    exit 1
  fi
done

# 4. Start frontend
echo "⚛️  Starting Next.js dashboard on :3000..."
cd "$FRONTEND_DIR"
# Ensure .env.local exists
[[ -f .env.local ]] || cp .env.example .env.local
npm run dev > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
echo "$FRONTEND_PID" >> "$PID_FILE"

# Wait for frontend
for i in {1..20}; do
  if curl -sf http://localhost:3000 >/dev/null 2>&1; then
    echo "✅ Frontend ready (pid $FRONTEND_PID)"
    break
  fi
  sleep 1
  if [[ $i -eq 20 ]]; then
    echo "❌ Frontend failed to start. Check $FRONTEND_LOG"
    tail -20 "$FRONTEND_LOG"
    exit 1
  fi
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🎉 JavaLB is running!"
echo ""
echo "   📊 Dashboard:   http://localhost:3000"
echo "   🔌 Backend API: http://localhost:8080"
echo "   📡 WS stream:   ws://localhost:8080/ws/events"
echo "   ❤️  Health:      http://localhost:8080/health"
echo ""
echo "   Logs: $BACKEND_LOG  |  $FRONTEND_LOG"
echo "   Press Ctrl+C to stop everything"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Keep script alive
wait