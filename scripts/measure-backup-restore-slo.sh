#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   BACKUP_ARTIFACT=./backups/dms-20260219T113728Z.archive.gz \
#   VERIFY_ELAPSED_SECONDS=1 \
#   ./scripts/measure-backup-restore-slo.sh
# Optional:
#   BACKUP_INTERVAL_SECONDS=86400
#   RPO_TARGET_SECONDS=86400
#   RTO_TARGET_SECONDS=3600

BACKUP_ARTIFACT="${BACKUP_ARTIFACT:-}"
VERIFY_ELAPSED_SECONDS="${VERIFY_ELAPSED_SECONDS:-}"
BACKUP_INTERVAL_SECONDS="${BACKUP_INTERVAL_SECONDS:-86400}"
RPO_TARGET_SECONDS="${RPO_TARGET_SECONDS:-86400}"
RTO_TARGET_SECONDS="${RTO_TARGET_SECONDS:-3600}"

if [[ -z "${BACKUP_ARTIFACT}" ]]; then
  echo "[slo] BACKUP_ARTIFACT is required" >&2
  exit 1
fi

if [[ -z "${VERIFY_ELAPSED_SECONDS}" ]]; then
  echo "[slo] VERIFY_ELAPSED_SECONDS is required" >&2
  exit 1
fi

if [[ ! -f "${BACKUP_ARTIFACT}" ]]; then
  echo "[slo] backup artifact not found: ${BACKUP_ARTIFACT}" >&2
  exit 1
fi

if ! [[ "${VERIFY_ELAPSED_SECONDS}" =~ ^[0-9]+$ ]]; then
  echo "[slo] VERIFY_ELAPSED_SECONDS must be an integer" >&2
  exit 1
fi

NOW_EPOCH="$(date -u +%s)"
ARTIFACT_EPOCH="$(date -u -r "${BACKUP_ARTIFACT}" +%s)"

RPO_SECONDS="$((NOW_EPOCH - ARTIFACT_EPOCH))"
if (( RPO_SECONDS < 0 )); then
  RPO_SECONDS=0
fi

RPO_OK="false"
RTO_OK="false"
if (( RPO_SECONDS <= RPO_TARGET_SECONDS )); then
  RPO_OK="true"
fi
if (( VERIFY_ELAPSED_SECONDS <= RTO_TARGET_SECONDS )); then
  RTO_OK="true"
fi

echo "[slo] backupArtifact=${BACKUP_ARTIFACT}"
echo "[slo] backupIntervalSeconds=${BACKUP_INTERVAL_SECONDS}"
echo "[slo] rpoSeconds=${RPO_SECONDS}"
echo "[slo] rpoTargetSeconds=${RPO_TARGET_SECONDS}"
echo "[slo] rpoWithinTarget=${RPO_OK}"
echo "[slo] rtoSeconds=${VERIFY_ELAPSED_SECONDS}"
echo "[slo] rtoTargetSeconds=${RTO_TARGET_SECONDS}"
echo "[slo] rtoWithinTarget=${RTO_OK}"

if [[ "${RPO_OK}" != "true" || "${RTO_OK}" != "true" ]]; then
  echo "[slo] status=OUT_OF_TARGET"
  exit 2
fi

echo "[slo] status=WITHIN_TARGET"