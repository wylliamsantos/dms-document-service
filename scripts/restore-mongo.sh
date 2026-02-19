#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   MONGO_URI=mongodb://localhost:27017/dms \
#   ARCHIVE_PATH=./backups/dms-20260219T000000Z.archive.gz \
#   ./scripts/restore-mongo.sh
# Optional:
#   DROP_BEFORE_RESTORE=true

MONGO_URI="${MONGO_URI:-mongodb://localhost:27017/dms}"
ARCHIVE_PATH="${ARCHIVE_PATH:-}"
DROP_BEFORE_RESTORE="${DROP_BEFORE_RESTORE:-false}"

if [[ -z "${ARCHIVE_PATH}" ]]; then
  echo "[restore] ARCHIVE_PATH is required" >&2
  exit 1
fi

if [[ ! -f "${ARCHIVE_PATH}" ]]; then
  echo "[restore] archive not found: ${ARCHIVE_PATH}" >&2
  exit 1
fi

EXTRA_ARGS=()
if [[ "${DROP_BEFORE_RESTORE}" == "true" ]]; then
  EXTRA_ARGS+=(--drop)
fi

echo "[restore] uri=${MONGO_URI}"
echo "[restore] archive=${ARCHIVE_PATH}"
echo "[restore] dropBeforeRestore=${DROP_BEFORE_RESTORE}"

mongorestore \
  --uri="${MONGO_URI}" \
  --gzip \
  --archive="${ARCHIVE_PATH}" \
  "${EXTRA_ARGS[@]}"

echo "[restore] done"