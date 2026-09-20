#!/usr/bin/env bash
# Creates the PostgreSQL databases ReLoop needs. Safe to re-run.
set -euo pipefail

DEV_DB="${DEV_DB:-reloop_dev}"
TEST_DB="${TEST_DB:-reloop_test}"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql not found. Install PostgreSQL first (e.g. 'brew install postgresql@18')." >&2
  exit 1
fi

if ! pg_isready >/dev/null 2>&1; then
  echo "PostgreSQL is not accepting connections. Start it first (e.g. 'brew services start postgresql@18')." >&2
  exit 1
fi

for db in "$DEV_DB" "$TEST_DB"; do
  if psql -tAc "SELECT 1 FROM pg_database WHERE datname = '$db'" postgres | grep -q 1; then
    echo "database '$db' already exists"
  else
    createdb "$db"
    echo "created database '$db'"
  fi
done

echo
echo "Done. Flyway applies the schema automatically when the backend starts."
echo "  dev  : $DEV_DB"
echo "  test : $TEST_DB   (used by 'cd backend && ./mvnw test')"
