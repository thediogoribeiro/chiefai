#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

YES=false
if [ "${1:-}" = "--yes" ]; then
  YES=true
fi

if [ "${SPRING_PROFILES_ACTIVE:-}" = "production" ]; then
  echo "Refusing to clean database while SPRING_PROFILES_ACTIVE=production" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose plugin is required" >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "Missing .env. Create it from .env.example first." >&2
  exit 1
fi

set -a
source .env
set +a

DB="${POSTGRES_DB:-personal_agent}"
USER="${POSTGRES_USER:-personal_agent}"

if [ "$DB" != "personal_agent" ]; then
  echo "Refusing to clean non-local database '$DB'. Expected personal_agent." >&2
  exit 1
fi

echo "This will clean schema public in local database: $DB"
if [ "$YES" != "true" ]; then
  read -r -p "Type CLEAN to continue: " confirmation
  if [ "$confirmation" != "CLEAN" ]; then
    echo "Cancelled."
    exit 1
  fi
fi

docker compose up -d postgres
docker compose exec -T postgres psql -U "$USER" -d "$DB" -v ON_ERROR_STOP=1 <<'SQL'
DROP SCHEMA IF EXISTS public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO PUBLIC;
SQL

echo "Local database schema cleaned. Flyway will reapply migrations on next application start."
