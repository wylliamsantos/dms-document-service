#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   MONGO_URI=mongodb://localhost:27017/dms ./scripts/backup-mongo.sh
# Optional:
#   BACKUP_DIR=./backups

MONGO_URI="${MONGO_URI:-mongodb://localhost:27017/dms}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_DIR="${BACKUP_DIR}/dms-${TS}"
ARCHIVE_PATH="${OUT_DIR}.archive.gz"

mkdir -p "${BACKUP_DIR}"

echo "[backup] uri=${MONGO_URI}"
echo "[backup] archive=${ARCHIVE_PATH}"

mongodump \
  --uri="${MONGO_URI}" \
  --gzip \
  --archive="${ARCHIVE_PATH}"

echo "[backup] done"
echo "[backup] artifact=${ARCHIVE_PATH}"