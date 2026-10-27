#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

command -v docker >/dev/null 2>&1 || { echo "Missing prerequisite: docker" >&2; exit 1; }
if ! docker info >/dev/null 2>&1; then
  echo "Docker is required for Testcontainers. Start Docker and try again." >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "No .env found. Creating one from .env.example for local test fallback."
  cp .env.example .env
fi

set -a
source .env
set +a

docker compose up -d postgres
for _ in {1..60}; do
  st="$(docker inspect --format='{{.State.Health.Status}}' personal-agent-postgres 2>/dev/null || true)"
  if [ "$st" = "healthy" ]; then
    break
  fi
  sleep 1
done

st="$(docker inspect --format='{{.State.Health.Status}}' personal-agent-postgres 2>/dev/null || true)"
if [ "$st" != "healthy" ]; then
  echo "PostgreSQL did not become healthy for test fallback." >&2
  exit 1
fi

exec ./mvnw verify
