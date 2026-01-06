#!/usr/bin/env bash
# // Implements System

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"
COMPOSE_BIN=${COMPOSE_BIN:-docker compose}

${COMPOSE_BIN} -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" down --remove-orphans