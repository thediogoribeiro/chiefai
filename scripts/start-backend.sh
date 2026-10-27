#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

need() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing prerequisite: $1" >&2; exit 1; }
}

need java
need docker

if ! docker compose version >/dev/null 2>&1; then
  echo "Docker Compose plugin is required: docker compose version failed" >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "No .env found. Creating one from .env.example for local use."
  cp .env.example .env
fi

set -a
source .env
set +a

echo "Starting local PostgreSQL dependency..."
docker compose up -d postgres

echo "Waiting for PostgreSQL healthcheck..."
for _ in {1..60}; do
  status="$(docker inspect --format='{{.State.Health.Status}}' personal-agent-postgres 2>/dev/null || true)"
  if [ "$status" = "healthy" ]; then
    break
  fi
  sleep 1
done

status="$(docker inspect --format='{{.State.Health.Status}}' personal-agent-postgres 2>/dev/null || true)"
if [ "$status" != "healthy" ]; then
  echo "PostgreSQL did not become healthy. Check: docker compose logs postgres" >&2
  exit 1
fi

echo "Starting Personal Agent Core on http://localhost:8080"
export DATABASE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_PORT:-5432}/${POSTGRES_DB:-personal_agent}"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
