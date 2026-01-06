#!/usr/bin/env bash
# // Implements System

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${ROOT_DIR}/docker-compose.yml"
ENV_FILE="${ROOT_DIR}/.env"
COMPOSE_BIN=${COMPOSE_BIN:-docker compose}

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Creating ${ENV_FILE} from template .env.example"
  cp "${ROOT_DIR}/.env.example" "${ENV_FILE}"
fi

${COMPOSE_BIN} -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" up -d --build --remove-orphans
${COMPOSE_BIN} -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" ps