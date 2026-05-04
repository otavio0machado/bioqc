#!/usr/bin/env bash

set -euo pipefail

RAILWAY_URL="${RAILWAY_URL:-https://<railway-url>}"
FRONTEND_URL="${FRONTEND_URL:-${VERCEL_URL:-https://<frontend-url>}}"
LOGIN_EMAIL="${LOGIN_EMAIL:-admin@bio.com}"
LOGIN_PASSWORD="${LOGIN_PASSWORD:-xxx}"

echo "1) Health check do backend"
curl --fail --silent --show-error "${RAILWAY_URL}/actuator/health" | jq .

echo
echo "2) Teste de login"
curl --fail --silent --show-error \
  -X POST "${RAILWAY_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"${LOGIN_EMAIL}\",\"password\":\"${LOGIN_PASSWORD}\"}" | jq .

echo
echo "3) Frontend responde"
curl --fail --silent --show-error -I "${FRONTEND_URL}"

echo
echo "4) Teste básico de CORS"
curl --fail --silent --show-error \
  -H "Origin: ${FRONTEND_URL}" \
  "${RAILWAY_URL}/api/dashboard/kpis" || true
