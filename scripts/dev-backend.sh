#!/usr/bin/env bash
# Starts the ReLoop backend on http://localhost:8080 for local development.
#
# Override anything by exporting it first, e.g.:
#   GEMINI_API_KEY=... ADMIN_EMAIL=me@example.com ./scripts/dev-backend.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# A JWT secret is mandatory; generate a throwaway one for local development only.
export JWT_SECRET="${JWT_SECRET:-$(head -c 48 /dev/urandom | base64 | tr -d '\n')}"

# Dev mode returns the password-reset token in the API response, because this build
# has no mail transport configured.
export RELOOP_DEV_MODE="${RELOOP_DEV_MODE:-true}"

# Bootstrap the first administrator account (only used when no admin exists yet).
# No password is baked into the repository: generate one and print it for local use.
export ADMIN_EMAIL="${ADMIN_EMAIL:-admin@reloop.local}"
if [ -z "${ADMIN_PASSWORD:-}" ]; then
  export ADMIN_PASSWORD="Admin#$(head -c 12 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 12)"
  echo "generated admin password: $ADMIN_PASSWORD"
  echo "  (set ADMIN_PASSWORD yourself to choose one; it is only used for the first bootstrap)"
fi

if [ -z "${GEMINI_API_KEY:-}" ]; then
  echo "note: GEMINI_API_KEY is not set — /api/waste/analyze will answer 503 and the UI will"
  echo "      fall back to manual classification. That is intentional, not a failure."
fi

echo "starting backend on http://localhost:8080 (admin: $ADMIN_EMAIL)"
cd "$ROOT_DIR/backend"
exec ./mvnw spring-boot:run
