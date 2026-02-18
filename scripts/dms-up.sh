#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ -z "${WORKDIR:-}" ]]; then
  if [[ "$(basename "$SCRIPT_DIR")" == "scripts" ]]; then
    WORKDIR="$(dirname "$SCRIPT_DIR")"
  else
    WORKDIR="$SCRIPT_DIR"
  fi
fi

STACK_DIR="$WORKDIR/dms-stack"
LOG_DIR="$WORKDIR/.dms-logs"

REPOS=(
  dms-document-service
  dms-search-service
  dms-watch-service
  dms-audit-service
  dms-frontend
)

mkdir -p "$WORKDIR" "$STACK_DIR" "$LOG_DIR"

echo "📁 WORKDIR: $WORKDIR"

PREBUILD_MODE="${PREBUILD_MODE:-test}" # test | build | skip

resolve_java() {
  local java_bin=""
  local java_home_candidate=""

  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    java_bin="${JAVA_HOME}/bin/java"
    java_home_candidate="${JAVA_HOME}"
  elif command -v java >/dev/null 2>&1; then
    java_bin="$(command -v java)"
    java_home_candidate="$(dirname "$(dirname "$(readlink -f "$java_bin")")")"
  fi

  if [[ -z "$java_bin" || ! -x "$java_bin" ]]; then
    echo "❌ Java não encontrado. Instale Java 21+ e/ou configure JAVA_HOME." >&2
    exit 1
  fi

  local major
  major="$($java_bin -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n1)"
  if [[ -z "$major" || "$major" -lt 21 ]]; then
    echo "❌ Java 21+ é obrigatório. Detectado: $($java_bin -version 2>&1 | head -n1)" >&2
    exit 1
  fi

  export JAVA_HOME="$java_home_candidate"
  export PATH="$JAVA_HOME/bin:$PATH"
  echo "✅ Java detectado: $($java_bin -version 2>&1 | head -n1)"
  echo "   JAVA_HOME=$JAVA_HOME"
}

prebuild_backend() {
  local repo_dir="$1"
  local mode="$2"

  case "$mode" in
    skip)
      echo "  - prebuild skip em $(basename "$repo_dir")"
      ;;
    test)
      echo "  - prebuild test em $(basename "$repo_dir")"
      (cd "$repo_dir" && ./gradlew test)
      ;;
    build)
      echo "  - prebuild build em $(basename "$repo_dir")"
      (cd "$repo_dir" && ./gradlew build -x test)
      ;;
    *)
      echo "❌ PREBUILD_MODE inválido: $mode (use: test | build | skip)" >&2
      exit 1
      ;;
  esac
}

seed_keycloak() {
  echo "  - aguardando Keycloak ficar disponível..."
  for _ in {1..60}; do
    if curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:8180/" | grep -qE "200|302|303"; then
      break
    fi
    sleep 2
  done

  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
    --server http://127.0.0.1:8180 --realm master --user admin --password admin123 >/dev/null

  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh create realms -s realm=dms -s enabled=true >/dev/null 2>&1 || true

  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh create clients -r dms \
    -s clientId=dms-frontend \
    -s enabled=true \
    -s publicClient=true \
    -s directAccessGrantsEnabled=true \
    -s standardFlowEnabled=true \
    -s 'redirectUris=["http://localhost:5173","http://localhost:5173/*","http://127.0.0.1:5173","http://127.0.0.1:5173/*"]' \
    -s 'webOrigins=["http://localhost:5173","http://127.0.0.1:5173"]' >/dev/null 2>&1 || true

  for role in OWNER ADMIN REVIEWER VIEWER DOCUMENT_VIEWER; do
    docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh create roles -r dms -s name=$role >/dev/null 2>&1 || true
  done

  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh create users -r dms -s username=testuser -s enabled=true -s firstName=Test -s lastName=User -s email=testuser@local.dev -s emailVerified=true >/dev/null 2>&1 || true
  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh set-password -r dms --username testuser --new-password test123 >/dev/null 2>&1 || true
  for role in DOCUMENT_VIEWER VIEWER; do
    docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh add-roles -r dms --uusername testuser --rolename $role >/dev/null 2>&1 || true
  done

  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh create users -r dms -s username=admin1 -s enabled=true -s firstName=Admin -s lastName=One -s email=admin1@local.dev -s emailVerified=true >/dev/null 2>&1 || true
  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh set-password -r dms --username admin1 --new-password admin123 >/dev/null 2>&1 || true
  for role in OWNER ADMIN REVIEWER VIEWER DOCUMENT_VIEWER; do
    docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh add-roles -r dms --uusername admin1 --rolename $role >/dev/null 2>&1 || true
  done

  local client_id
  client_id=$(docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh get clients -r dms -q clientId=dms-frontend | python3 -c 'import sys,json;print(json.load(sys.stdin)[0]["id"])')

  docker exec dms-keycloak /opt/keycloak/bin/kcadm.sh create clients/$client_id/protocol-mappers/models -r dms \
    -s name=tenant_id_hardcoded \
    -s protocol=openid-connect \
    -s protocolMapper=oidc-hardcoded-claim-mapper \
    -s 'config."claim.name"=tenant_id' \
    -s 'config."claim.value"=tenant-dev' \
    -s 'config."jsonType.label"=String' \
    -s 'config."id.token.claim"=true' \
    -s 'config."access.token.claim"=true' >/dev/null 2>&1 || true

  local discovery_code
  discovery_code=$(curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:8180/realms/dms/.well-known/openid-configuration" || true)
  if [[ "$discovery_code" != "200" ]]; then
    echo "❌ Keycloak dms realm não ficou pronto (well-known HTTP $discovery_code)." >&2
    exit 1
  fi

  echo "  - Keycloak realm dms pronto"
}

cat > "$STACK_DIR/docker-compose.yml" <<'YAML'
services:
  mongo:
    image: mongo:7
    container_name: dms-mongo
    restart: unless-stopped
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: root
      MONGO_INITDB_DATABASE: dms
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
      - ./mongo-init.js:/docker-entrypoint-initdb.d/mongo-init.js:ro

  redis:
    image: redis:7-alpine
    container_name: dms-redis
    restart: unless-stopped
    ports:
      - "6379:6379"

  minio:
    image: minio/minio:latest
    container_name: dms-minio
    restart: unless-stopped
    environment:
      MINIO_ROOT_USER: dms
      MINIO_ROOT_PASSWORD: dmssecret
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    volumes:
      - minio-data:/data

  rabbitmq:
    image: rabbitmq:3-management
    container_name: dms-rabbitmq
    restart: unless-stopped
    ports:
      - "5672:5672"
      - "15672:15672"

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.14.3
    container_name: dms-elasticsearch
    restart: unless-stopped
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"

  keycloak:
    image: quay.io/keycloak/keycloak:25.0
    container_name: dms-keycloak
    restart: unless-stopped
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin123
    command: start-dev --http-port=8180
    ports:
      - "8180:8180"

volumes:
  mongo-data:
  minio-data:
YAML

cat > "$STACK_DIR/mongo-init.js" <<'JS'
const dbName = 'dms';
const user = 'dms';
const pass = 'dms';

db = db.getSiblingDB(dbName);
try {
  db.createUser({
    user,
    pwd: pass,
    roles: [{ role: 'readWrite', db: dbName }]
  });
} catch (e) {
  print('mongo init warning: ' + e.message);
}
JS

echo "[1/9] Detectando Java..."
resolve_java

echo "[2/9] Clonando/atualizando repositórios..."
for repo in "${REPOS[@]}"; do
  if [[ -d "$WORKDIR/$repo/.git" ]]; then
    git -C "$WORKDIR/$repo" fetch --all --prune >/dev/null 2>&1 || true
  else
    git clone "https://github.com/wylliamsantos/$repo.git" "$WORKDIR/$repo"
  fi
done

echo "[3/9] Subindo infraestrutura (docker compose)..."
docker compose -f "$STACK_DIR/docker-compose.yml" up -d

echo "[4/9] Seed do Keycloak (realm/client/user)..."
seed_keycloak

echo "[5/9] Preparando bucket MinIO dms-local..."
docker run --rm --network host minio/mc alias set local http://127.0.0.1:9000 dms dmssecret >/dev/null 2>&1 || true
docker run --rm --network host minio/mc mb -p local/dms-local >/dev/null 2>&1 || true

echo "[6/9] Configurando frontend para modo local..."
cat > "$WORKDIR/dms-frontend/.env.local" <<'ENV'
VITE_PORT=5173
VITE_DOCUMENT_API_BASE_URL=http://localhost:8080
VITE_SEARCH_API_BASE_URL=http://localhost:8081
VITE_DEFAULT_TRANSACTION_ID=dev-transaction
VITE_DEFAULT_AUTH_BEARER=
VITE_KEYCLOAK_URL=http://localhost:8180
VITE_KEYCLOAK_REALM=dms
VITE_KEYCLOAK_CLIENT_ID=dms-frontend
VITE_KEYCLOAK_REDIRECT_URI=http://localhost:5173
VITE_IDP_REDIRECT_URI=http://localhost:5173
VITE_IDP_POST_LOGOUT_REDIRECT_URI=http://localhost:5173
ENV

start_or_restart() {
  local name="$1"
  local cmd="$2"
  local log="$3"

  if pgrep -f "$name" >/dev/null 2>&1; then
    echo "  - reiniciando $name"
    pkill -f "$name" >/dev/null 2>&1 || true
    sleep 1
  else
    echo "  - iniciando $name"
  fi

  nohup bash -lc "$cmd" >"$log" 2>&1 &
}

echo "[7/9] Prebuild backend (${PREBUILD_MODE})..."
prebuild_backend "$WORKDIR/dms-document-service" "$PREBUILD_MODE"
prebuild_backend "$WORKDIR/dms-search-service" "$PREBUILD_MODE"
prebuild_backend "$WORKDIR/dms-watch-service" "$PREBUILD_MODE"
prebuild_backend "$WORKDIR/dms-audit-service" "$PREBUILD_MODE"

echo "[8/9] Subindo serviços da aplicação..."
CORS_ORIGINS="${DMS_CORS_ALLOWED_ORIGINS:-http://localhost:5173,http://127.0.0.1:5173}"

start_or_restart "dms-document-service.*bootRun" "cd '$WORKDIR/dms-document-service' && export JAVA_HOME='$JAVA_HOME' && export PATH='$JAVA_HOME/bin:$PATH' && export DMS_CORS_ALLOWED_ORIGINS='$CORS_ORIGINS' && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun" "$LOG_DIR/document.log"
start_or_restart "dms-search-service.*bootRun" "cd '$WORKDIR/dms-search-service' && export JAVA_HOME='$JAVA_HOME' && export PATH='$JAVA_HOME/bin:$PATH' && export DMS_CORS_ALLOWED_ORIGINS='$CORS_ORIGINS' && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun" "$LOG_DIR/search.log"
start_or_restart "dms-watch-service.*bootRun" "cd '$WORKDIR/dms-watch-service' && export JAVA_HOME='$JAVA_HOME' && export PATH='$JAVA_HOME/bin:$PATH' && export DMS_CORS_ALLOWED_ORIGINS='$CORS_ORIGINS' && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun" "$LOG_DIR/watch.log"
start_or_restart "dms-audit-service.*bootRun" "cd '$WORKDIR/dms-audit-service' && export JAVA_HOME='$JAVA_HOME' && export PATH='$JAVA_HOME/bin:$PATH' && export DMS_CORS_ALLOWED_ORIGINS='$CORS_ORIGINS' && SPRING_PROFILES_ACTIVE=local ./gradlew bootRun" "$LOG_DIR/audit.log"
start_or_restart "dms-frontend.*vite --host localhost --port 5173" "cd '$WORKDIR/dms-frontend' && npm ci && npm run dev -- --host localhost --port 5173" "$LOG_DIR/frontend.log"

echo "[9/9] Health check rápido..."
sleep 5
for url in \
  "http://localhost:5173" \
  "http://localhost:8080/actuator/health" \
  "http://localhost:8081/actuator/health" \
  "http://localhost:8082/actuator/health" \
  "http://localhost:8092/actuator/health"; do
  code=$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)
  echo "  - $url -> HTTP $code"
done

APP_URL="http://localhost:5173"
echo
echo "✅ Ambiente DMS iniciado"
echo "Frontend: $APP_URL"
echo "Logs: $LOG_DIR"

auto_open() {
  if command -v xdg-open >/dev/null 2>&1; then
    xdg-open "$APP_URL" >/dev/null 2>&1 || true
  elif command -v open >/dev/null 2>&1; then
    open "$APP_URL" >/dev/null 2>&1 || true
  fi
}

if [[ -n "${SSH_CONNECTION:-}" ]]; then
  echo
  echo "Você está em SSH. Na sua máquina local, abra com túnel:"
  echo "ssh -L 5173:127.0.0.1:5173 -L 8080:127.0.0.1:8080 -L 8081:127.0.0.1:8081 -L 8082:127.0.0.1:8082 -L 8092:127.0.0.1:8092 <usuario>@<host>"
  echo "Depois acesse: $APP_URL"
else
  auto_open
fi
