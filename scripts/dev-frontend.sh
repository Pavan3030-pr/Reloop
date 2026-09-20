#!/usr/bin/env bash
# Starts the ReLoop web client on http://localhost:5173, proxying /api and /uploads
# to the backend (override with BACKEND_URL=http://host:port).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/frontend"

if [ ! -d node_modules ]; then
  echo "installing frontend dependencies..."
  npm install --no-audit --no-fund
fi

echo "starting frontend on http://localhost:5173 (API proxy → ${BACKEND_URL:-http://localhost:8080})"
exec npm run dev
