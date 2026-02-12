#!/usr/bin/env bash
set -euo pipefail

DOC_API_URL="${DOC_API_URL:-http://127.0.0.1:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
REALM="${KEYCLOAK_REALM:-dms}"
CLIENT_ID="${KEYCLOAK_CLIENT_ID:-dms-frontend}"
VIEWER_USER="${KEYCLOAK_VIEWER_USER:-viewer1}"
VIEWER_PASS="${KEYCLOAK_VIEWER_PASS:-view123}"
ADMIN_USER="${KEYCLOAK_ADMIN_USER:-testuser}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASS:-test123}"
ORIGIN="${CORS_ORIGIN:-http://localhost:5173}"

if ! command -v curl >/dev/null 2>&1; then
  echo "❌ curl não encontrado" >&2
  exit 1
fi
if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ python3 não encontrado" >&2
  exit 1
fi

fetch_token() {
  local user="$1"
  local pass="$2"
  curl -s -X POST "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "client_id=${CLIENT_ID}" \
    --data-urlencode "username=${user}" \
    --data-urlencode "password=${pass}" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin).get("access_token",""))'
}

http_code() {
  local method="$1"
  local url="$2"
  shift 2
  curl -s -o /tmp/package-a-last.out -w '%{http_code}' -X "$method" "$url" "$@"
}

assert_code() {
  local name="$1"
  local got="$2"
  local expected="$3"
  if [[ "$got" == "$expected" ]]; then
    echo "✅ $name -> HTTP $got"
  else
    echo "❌ $name -> esperado HTTP $expected, veio $got" >&2
    echo "--- body ---" >&2
    cat /tmp/package-a-last.out >&2 || true
    exit 1
  fi
}

echo "[1/6] Checando saúde dos serviços..."
assert_code "document health" "$(http_code GET "${DOC_API_URL}/actuator/health")" "200"
assert_code "keycloak realm discovery" "$(http_code GET "${KEYCLOAK_URL}/realms/${REALM}/.well-known/openid-configuration")" "200"

echo "[2/6] Obtendo token viewer/admin..."
VIEWER_TOKEN="$(fetch_token "$VIEWER_USER" "$VIEWER_PASS")"
ADMIN_TOKEN="$(fetch_token "$ADMIN_USER" "$ADMIN_PASS")"

if [[ -z "$VIEWER_TOKEN" ]]; then
  echo "❌ não consegui token viewer (${VIEWER_USER})" >&2
  exit 1
fi
if [[ -z "$ADMIN_TOKEN" ]]; then
  echo "❌ não consegui token admin (${ADMIN_USER})" >&2
  exit 1
fi
echo "✅ tokens obtidos"

echo "[3/6] Validando proteção de endpoint (401 sem token)..."
assert_code "GET /v1/categories/all sem token" \
  "$(http_code GET "${DOC_API_URL}/v1/categories/all" -H 'TransactionId: smoke-no-auth')" \
  "401"

echo "[4/6] Validando autorização por role (403 viewer em endpoint admin)..."
assert_code "POST /v1/documents/base64 com viewer" \
  "$(http_code POST "${DOC_API_URL}/v1/documents/base64" \
    -H "Authorization: Bearer ${VIEWER_TOKEN}" \
    -H 'transactionId: smoke-viewer' \
    -H 'Content-Type: application/json' \
    --data '{"category":"CAT","filename":"a.txt","author":"viewer","documentBase64":"dGVzdA==","metadados":{}}')" \
  "403"

echo "[5/6] Validando regras de payload (400 com campos obrigatórios inválidos)..."
assert_code "POST /v1/documents/base64 com admin e payload inválido" \
  "$(http_code POST "${DOC_API_URL}/v1/documents/base64" \
    -H "Authorization: Bearer ${ADMIN_TOKEN}" \
    -H 'transactionId: smoke-admin-invalid' \
    -H 'Content-Type: application/json' \
    --data '{"category":"","filename":"","author":"","documentBase64":"","metadados":{}}')" \
  "400"

echo "[6/6] Validando CORS preflight..."
assert_code "OPTIONS /v1/categories/all" \
  "$(http_code OPTIONS "${DOC_API_URL}/v1/categories/all" \
    -H "Origin: ${ORIGIN}" \
    -H 'Access-Control-Request-Method: GET' \
    -H 'Access-Control-Request-Headers: Authorization,TransactionId')" \
  "200"

echo
echo "🎯 Package A smoke: OK"
