#!/usr/bin/env bash
set -euo pipefail

# End-to-end backup/restore drill for alpha operations.
#
# Default mode assumes mongodump/mongorestore installed on host.
# Set USE_DOCKER_EXEC=true to run against a Mongo container (default: dms-mongo).
#
# Required env:
#   MONGO_URI
# Optional env:
#   ARTIFACT_PATH=./backups/dms-<UTC>.archive.gz
#   DROP_BEFORE_RESTORE=true
#   VERIFY_ONLY=false
#   USE_DOCKER_EXEC=false
#   MONGO_CONTAINER_NAME=dms-mongo
#   HEALTHCHECK_URL=http://localhost:18080/actuator/health
#   RPO_TARGET_SECONDS=86400
#   RTO_TARGET_SECONDS=3600

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOG_DIR="${ROOT_DIR}/.dms-logs"
mkdir -p "${LOG_DIR}" "${ROOT_DIR}/backups"

MONGO_URI="${MONGO_URI:-}"
ARTIFACT_PATH="${ARTIFACT_PATH:-${ROOT_DIR}/backups/dms-$(date -u +%Y%m%dT%H%M%SZ).archive.gz}"
DROP_BEFORE_RESTORE="${DROP_BEFORE_RESTORE:-true}"
VERIFY_ONLY="${VERIFY_ONLY:-false}"
USE_DOCKER_EXEC="${USE_DOCKER_EXEC:-false}"
MONGO_CONTAINER_NAME="${MONGO_CONTAINER_NAME:-dms-mongo}"
HEALTHCHECK_URL="${HEALTHCHECK_URL:-http://localhost:18080/actuator/health}"
RPO_TARGET_SECONDS="${RPO_TARGET_SECONDS:-86400}"
RTO_TARGET_SECONDS="${RTO_TARGET_SECONDS:-3600}"

if [[ -z "${MONGO_URI}" ]]; then
  echo "[drill] MONGO_URI is required" >&2
  exit 1
fi

LOG_FILE="${LOG_DIR}/backup-restore-drill-$(date -u +%Y%m%dT%H%M%SZ).log"
exec > >(tee -a "${LOG_FILE}") 2>&1

echo "[drill] start=$(date -u +%FT%TZ)"
echo "[drill] artifact=${ARTIFACT_PATH}"
echo "[drill] mode=$( [[ "${USE_DOCKER_EXEC}" == "true" ]] && echo docker-exec || echo host-binaries )"

backup_host() {
  MONGO_URI="${MONGO_URI}" BACKUP_ARTIFACT="${ARTIFACT_PATH}" "${SCRIPT_DIR}/backup-mongo.sh"
}

verify_host() {
  MONGO_URI="${MONGO_URI}" ARCHIVE_PATH="${ARTIFACT_PATH}" "${SCRIPT_DIR}/verify-restore-mongo.sh"
}

restore_host() {
  MONGO_URI="${MONGO_URI}" ARCHIVE_PATH="${ARTIFACT_PATH}" DROP_BEFORE_RESTORE="${DROP_BEFORE_RESTORE}" "${SCRIPT_DIR}/restore-mongo.sh"
}

backup_docker() {
  docker exec "${MONGO_CONTAINER_NAME}" sh -lc \
    "mongodump --uri='${MONGO_URI}' --gzip --archive" > "${ARTIFACT_PATH}"
}

verify_docker() {
  docker exec -i "${MONGO_CONTAINER_NAME}" sh -lc \
    "mongorestore --uri='${MONGO_URI}' --gzip --archive --dryRun" < "${ARTIFACT_PATH}"
}

restore_docker() {
  local drop_flag=""
  if [[ "${DROP_BEFORE_RESTORE}" == "true" ]]; then
    drop_flag="--drop"
  fi
  docker exec -i "${MONGO_CONTAINER_NAME}" sh -lc \
    "mongorestore --uri='${MONGO_URI}' --gzip --archive ${drop_flag}" < "${ARTIFACT_PATH}"
}

if [[ "${USE_DOCKER_EXEC}" == "true" ]]; then
  backup_docker
else
  backup_host
fi

echo "[drill] backup done"

VERIFY_START="$(date -u +%s)"
if [[ "${USE_DOCKER_EXEC}" == "true" ]]; then
  verify_docker
else
  verify_host
fi
VERIFY_END="$(date -u +%s)"
VERIFY_ELAPSED_SECONDS="$((VERIFY_END - VERIFY_START))"

echo "[drill] verifyRestoreElapsedSeconds=${VERIFY_ELAPSED_SECONDS}"

if [[ "${VERIFY_ONLY}" != "true" ]]; then
  if [[ "${USE_DOCKER_EXEC}" == "true" ]]; then
    restore_docker
  else
    restore_host
  fi
  echo "[drill] restore done"

  HEALTH_STATUS="$(curl -sS -o /tmp/backup-restore-health.json -w "%{http_code}" "${HEALTHCHECK_URL}" || true)"
  echo "[drill] healthcheckStatus=${HEALTH_STATUS} url=${HEALTHCHECK_URL}"
  if [[ "${HEALTH_STATUS}" != "200" ]]; then
    echo "[drill] healthcheck failed after restore" >&2
    exit 1
  fi
fi

BACKUP_ARTIFACT="${ARTIFACT_PATH}" \
VERIFY_ELAPSED_SECONDS="${VERIFY_ELAPSED_SECONDS}" \
RPO_TARGET_SECONDS="${RPO_TARGET_SECONDS}" \
RTO_TARGET_SECONDS="${RTO_TARGET_SECONDS}" \
"${SCRIPT_DIR}/measure-backup-restore-slo.sh"

echo "[drill] status=OK"
echo "[drill] logFile=${LOG_FILE}"
