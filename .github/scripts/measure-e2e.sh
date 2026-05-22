#!/bin/bash
# Sweep an FTS transfer process across multiple patient-set sizes (defaulting
# to the gics-consent-example project) and capture per-container resource
# samples for each run.
#
# Idempotent setup:
#   - Compose stack is brought up via `make start` if not already running.
#   - gICS volume is wiped + re-seeded once at the start so the measurement
#     baseline is the canonical 100-patient seed shipped in gics/initdb.
#   - Test data for max(SIZES) is downloaded once into .github/test/test-data/<share>/
#     (skipped when checksum verification passes).
#   - Test data is uploaded to cd-hds once when the Patient count there does not
#     match max(SIZES). cd-hds is NEVER reset between runs.
#
# Before every run (smallest-first, repeated RUNS times):
#   - rd-hds, gpas, gpas-db, and the cd/tc/rd agents are torn down and
#     re-created so each run sees a clean target FHIR store, a cold pseudonym
#     cache, and fresh agent JVMs (no warm WebClient pools or JIT state).
#   - gICS and cd-hds are preserved across runs.
#
# Consent management:
#   - gICS state grows monotonically. The delta [current..N) of new consents is
#     uploaded once before the first run of size N; subsequent runs at the same
#     N add nothing.
#
# Measurement:
#   - measure-resources.sh runs in the background, sampling docker stats every
#     INTERVAL seconds for every container in the compose project.
#   - The transfer is executed via `make transfer-all` (TcaCohortSelector pulls
#     the cohort from gICS, so cohort size == current consent count).
#   - Wall-clock duration and the full process status are written to per-run
#     meta. A non-COMPLETED phase is reported loudly but does not abort the
#     sweep — other sizes still get measured.
#
# Output (in OUT_DIR):
#   host.json                     — host metadata recorded once
#   run-<N>-<R>.csv               — docker stats samples
#   run-<N>-<R>.meta.json         — per-run started/ended/duration/phase
#
# Env knobs (defaults shown):
#   SIZES="100 1000 10000"
#   RUNS=3
#   PROJECT=gics-consent-example
#   SHARE=MioAzTLMjzbPNyx
#   OUT_DIR=measurements
#   INTERVAL=2
#   COMPOSE_PROJECT=fts-test
#   SEEDED_CONSENTS=100      # consents shipped in gics-db init scripts
#   CONCURRENCY=1            # informational only
#   MAX_SEND_CONCURRENCY=32  # cd-agent runner.maxSendConcurrency (Spring relaxed
#                            # binding via RUNNER_MAXSENDCONCURRENCY env). Picked
#                            # up on each per-run cd-agent restart.
#   LOCAL_BUNDLES_FILE=""    # path to a gzipped ndjson of transaction bundles
#                            # (one Bundle per line). When set together with
#                            # LOCAL_AUTHORED_JSON the remote test-data download
#                            # is skipped and these files drive cd-hds upload
#                            # and consent enumeration. Intended for datasets
#                            # not hosted on speicherwolke (e.g. synthea output).
#   LOCAL_AUTHORED_JSON=""   # path to a {patientId: date} JSON map matching
#                            # the bundles. Must be set iff LOCAL_BUNDLES_FILE
#                            # is set. Patient IDs become the consent keys.
#   REUSE_STATE=0            # when 1, reuse existing cd-hds bundle data
#                            # (skip the upload entirely). gICS is still wiped
#                            # + re-seeded and consents are still uploaded per
#                            # size, so cohort selection is honest.
#                            # Use when cd-hds already holds the dataset (e.g.
#                            # re-running after fixing infra issues) and you
#                            # don't want to pay the 100k bundle upload cost
#                            # again — at -c8 it takes ~10min for 100k.
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
TEST_DIR="$(readlink -f "${SCRIPT_DIR}/../test")"

SIZES="${SIZES:-100 1000 10000}"
RUNS="${RUNS:-3}"
PROJECT="${PROJECT:-gics-consent-example}"
SHARE="${SHARE:-MioAzTLMjzbPNyx}"
OUT_DIR="${OUT_DIR:-${TEST_DIR}/measurements}"
INTERVAL="${INTERVAL:-2}"
COMPOSE_PROJECT="${COMPOSE_PROJECT:-fts-test}"
SEEDED_CONSENTS="${SEEDED_CONSENTS:-100}"
CONCURRENCY="${CONCURRENCY:-1}"
MAX_SEND_CONCURRENCY="${MAX_SEND_CONCURRENCY:-32}"
export MAX_SEND_CONCURRENCY  # consumed by cd-agent compose env interpolation
LOCAL_BUNDLES_FILE="${LOCAL_BUNDLES_FILE:-}"
LOCAL_AUTHORED_JSON="${LOCAL_AUTHORED_JSON:-}"
REUSE_STATE="${REUSE_STATE:-0}"

if [ -n "${LOCAL_BUNDLES_FILE}" ] || [ -n "${LOCAL_AUTHORED_JSON}" ]; then
  if [ -z "${LOCAL_BUNDLES_FILE}" ] || [ -z "${LOCAL_AUTHORED_JSON}" ]; then
    echo "[measure-e2e] ERROR: LOCAL_BUNDLES_FILE and LOCAL_AUTHORED_JSON must both be set or both unset" >&2
    exit 2
  fi
  LOCAL_BUNDLES_FILE="$(readlink -f "${LOCAL_BUNDLES_FILE}")"
  LOCAL_AUTHORED_JSON="$(readlink -f "${LOCAL_AUTHORED_JSON}")"
  if [ ! -f "${LOCAL_BUNDLES_FILE}" ]; then
    echo "[measure-e2e] ERROR: LOCAL_BUNDLES_FILE=${LOCAL_BUNDLES_FILE} not found" >&2
    exit 2
  fi
  if [ ! -f "${LOCAL_AUTHORED_JSON}" ]; then
    echo "[measure-e2e] ERROR: LOCAL_AUTHORED_JSON=${LOCAL_AUTHORED_JSON} not found" >&2
    exit 2
  fi
fi
USE_LOCAL_DATA="$( [ -n "${LOCAL_BUNDLES_FILE}" ] && echo 1 || echo 0 )"

read -r -a SIZE_ARR <<< "${SIZES}"
IFS=$'\n' SIZE_ARR=($(printf '%s\n' "${SIZE_ARR[@]}" | sort -n))
unset IFS
MAX_N="${SIZE_ARR[${#SIZE_ARR[@]}-1]}"

mkdir -p "${OUT_DIR}"
cd "${TEST_DIR}"

log()  { printf '[measure-e2e] %s\n' "$*"; }
warn() { printf '[measure-e2e] WARN: %s\n' "$*" >&2; }

ensure_stack_up() {
  if docker compose -p "${COMPOSE_PROJECT}" ps --status=running --format '{{.Name}}' | grep -q .; then
    log "compose stack '${COMPOSE_PROJECT}' already running"
    return
  fi
  if [ ! -f "${TEST_DIR}/ssl/server.crt" ]; then
    log "ssl/server.crt missing — running 'make generate-certs'"
    make generate-certs
  fi
  log "compose stack not running — invoking 'make start' (gics/gpas warmup ~60-180s)"
  make start
}

# `down -v` removes the anonymous mysql data volume so initdb scripts in
# gics/initdb/ re-run on the next `up` and we land at exactly the seeded
# baseline. Without `-v`, mysql data persists across down/up.
wipe_gics_to_seed() {
  log "wiping gics + gics-db volumes to restore seeded baseline (${SEEDED_CONSENTS} consents)"
  docker compose -p "${COMPOSE_PROJECT}" down -v gics gics-db
  docker compose -p "${COMPOSE_PROJECT}" up --wait gics gics-db
}

# When using local test data, the seeded consents reference UUIDs that don't
# match the local Patient IDs. Truncate every consent-related table after the
# seed init scripts have run, then restart gics so its JPA caches are flushed.
# We rely on the canonical initdb tables enumerated in gics/initdb/03_consent.sql.gz.
truncate_gics_consents() {
  log "truncating gICS consent tables (local-data mode — seed UUIDs would not match bundles)"
  docker compose -p "${COMPOSE_PROJECT}" exec -T gics-db \
    mysql -uroot -proot gics -e "
      SET FOREIGN_KEY_CHECKS=0;
      TRUNCATE TABLE consent;
      TRUNCATE TABLE qc;
      TRUNCATE TABLE qc_hist;
      TRUNCATE TABLE signature;
      TRUNCATE TABLE signed_policy;
      TRUNCATE TABLE signer_id;
      TRUNCATE TABLE virtual_person;
      TRUNCATE TABLE virtual_person_signer_id;
      SET FOREIGN_KEY_CHECKS=1;
    "
  log "restarting gics to flush JPA caches"
  docker compose -p "${COMPOSE_PROJECT}" restart gics
  docker compose -p "${COMPOSE_PROJECT}" up --wait gics
}

record_host() {
  local host_file="${OUT_DIR}/host.json"
  log "recording host metadata -> ${host_file}"
  jq -n \
    --arg uname "$(uname -a)" \
    --arg kernel "$(uname -r)" \
    --arg arch "$(uname -m)" \
    --arg cpu_model "$(LC_ALL=C lscpu 2>/dev/null | awk -F: '/^Model name/ {sub(/^ +/,"",$2); print $2; exit}')" \
    --arg cpu_count "$(nproc 2>/dev/null || echo 0)" \
    --arg mem_total_kb "$(awk '/^MemTotal:/ {print $2}' /proc/meminfo 2>/dev/null || echo 0)" \
    --arg docker_version "$(docker --version 2>/dev/null || echo unknown)" \
    --arg compose_version "$(docker compose version --short 2>/dev/null || echo unknown)" \
    --arg recorded_at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{ unameFull: $uname, kernel: $kernel, arch: $arch,
       cpuModel: $cpu_model, cpuCount: ($cpu_count|tonumber),
       memTotalKb: ($mem_total_kb|tonumber),
       dockerVersion: $docker_version, composeVersion: $compose_version,
       recordedAt: $recorded_at }' \
    >"${host_file}"
}

ensure_test_data() {
  local n="${1}"
  if [ "${USE_LOCAL_DATA}" = "1" ]; then
    log "LOCAL_BUNDLES_FILE=${LOCAL_BUNDLES_FILE} — skipping remote download"
    return
  fi
  log "ensuring test data for share=${SHARE} size=${n}"
  make download-test-data-checksums >/dev/null
  if make check-test-data >/dev/null 2>&1; then
    log "test data checksum OK — skipping download"
    return
  fi
  TEST_SET_SIZE="${n}" make download-test-data
}

cd_hds_patient_count() {
  local url
  url="http://$(docker compose -p "${COMPOSE_PROJECT}" port cd-hds 8080)/fhir/Patient?_summary=count"
  curl -sSf "${url}" | jq -r .total
}

ensure_cd_hds_loaded() {
  local n="${1}"
  local current
  current="$(cd_hds_patient_count)"
  log "cd-hds Patient count=${current} (expected>=${n})"
  if [ "${current}" -ge "${n}" ]; then
    log "cd-hds already loaded — skipping upload"
    return
  fi
  log "cd-hds underloaded — uploading test data (one-time)"
  if [ "${USE_LOCAL_DATA}" = "1" ]; then
    log "using local bundles ${LOCAL_BUNDLES_FILE}"
    COMPOSE_PROJECT="${COMPOSE_PROJECT}" \
      "${SCRIPT_DIR}/upload-data-local.sh" "${LOCAL_BUNDLES_FILE}"
  else
    make upload-test-data
  fi
}

# Bouncing rd-hds + gpas + gpas-db gives each run a cold pseudonym cache and a
# clean target FHIR store. gICS and cd-hds are deliberately preserved so
# consents accumulate monotonically and the bulk patient data is not re-
# uploaded. The three FTS agents are restarted with their dependencies because
# their reactive WebClient pools hold sockets to rd-hds/gpas/tc-agent and don't
# recover cleanly from a backend restart, leading to the RDA appearing "down"
# and bundles being skipped.
reset_per_run_state() {
  log "resetting rd-hds + gpas + gpas-db + cd-agent + tc-agent + rd-agent (gics + cd-hds preserved)"
  docker compose -p "${COMPOSE_PROJECT}" down \
    rd-agent tc-agent cd-agent rd-hds gpas gpas-db
  docker compose -p "${COMPOSE_PROJECT}" up --wait \
    rd-hds gpas gpas-db tc-agent cd-agent rd-agent
}

# Tracks how many consents are currently in gICS so we can upload only the
# delta when growing to the next size. Started from SEEDED_CONSENTS after
# wipe_gics_to_seed() and updated in place by ensure_consents_for().
CURRENT_CONSENTS="${SEEDED_CONSENTS}"

ensure_consents_for() {
  local n="${1}"
  if [ "${n}" -le "${CURRENT_CONSENTS}" ]; then
    log "gICS already has ${CURRENT_CONSENTS} consents (>= ${n}) — no upload"
    return
  fi
  log "uploading consent delta [${CURRENT_CONSENTS}..${n})"
  if [ "${USE_LOCAL_DATA}" = "1" ]; then
    LOCAL_AUTHORED_JSON="${LOCAL_AUTHORED_JSON}" \
      "${SCRIPT_DIR}/upload-consents-local-range.sh" "${CURRENT_CONSENTS}" "${n}"
  else
    "${SCRIPT_DIR}/upload-consents-range.sh" "${CURRENT_CONSENTS}" "${n}"
  fi
  CURRENT_CONSENTS="${n}"
}

run_one() {
  local n="${1}" r="${2}"
  local csv="${OUT_DIR}/run-${n}-${r}.csv"
  local meta="${OUT_DIR}/run-${n}-${r}.meta.json"
  local started ended started_epoch ended_epoch duration_s phase
  log "=== size N=${n} run=${r}/${RUNS} ==="

  reset_per_run_state

  log "starting sampler -> ${csv}"
  : >"${csv}"
  "${SCRIPT_DIR}/measure-resources.sh" "${csv}" "${INTERVAL}" "${COMPOSE_PROJECT}" &
  local sampler_pid=$!
  trap 'kill "${sampler_pid}" 2>/dev/null || true' INT TERM

  started_epoch="$(date -u +%s)"
  started="$(date -u -d "@${started_epoch}" +%Y-%m-%dT%H:%M:%SZ)"
  make transfer-all PROJECT="${PROJECT}"
  make wait
  ended_epoch="$(date -u +%s)"
  ended="$(date -u -d "@${ended_epoch}" +%Y-%m-%dT%H:%M:%SZ)"
  duration_s=$((ended_epoch - started_epoch))

  kill "${sampler_pid}" 2>/dev/null || true
  wait "${sampler_pid}" 2>/dev/null || true
  trap - INT TERM

  local status
  status="$(curl -sSf "$(cat "${TEST_DIR}/process.url")")"
  phase="$(echo "${status}" | jq -r .phase)"
  jq -n \
    --arg n "${n}" --arg r "${r}" --arg project "${PROJECT}" \
    --arg started "${started}" --arg ended "${ended}" \
    --arg duration "${duration_s}" \
    --arg phase "${phase}" --arg interval "${INTERVAL}" \
    --arg concurrency "${CONCURRENCY}" \
    --arg maxSendConcurrency "${MAX_SEND_CONCURRENCY}" \
    --argjson status "${status}" \
    '{ size: ($n|tonumber), runIndex: ($r|tonumber), project: $project,
       started: $started, ended: $ended, durationSeconds: ($duration|tonumber),
       phase: $phase, intervalSeconds: ($interval|tonumber),
       concurrency: ($concurrency|tonumber),
       maxSendConcurrency: ($maxSendConcurrency|tonumber),
       processStatus: $status }' \
    >"${meta}"

  if [ "${phase}" != "COMPLETED" ]; then
    warn "size N=${n} run=${r} ended with phase=${phase} (sentBundles=$(echo "${status}" | jq -r .sentBundles), skippedBundles=$(echo "${status}" | jq -r .skippedBundles)) — measurement likely invalid"
  else
    log "size N=${n} run=${r} done — phase=${phase} duration=${duration_s}s"
  fi
}

main() {
  log "config: SIZES=${SIZES} RUNS=${RUNS} MAX_SEND_CONCURRENCY=${MAX_SEND_CONCURRENCY} OUT_DIR=${OUT_DIR} REUSE_STATE=${REUSE_STATE}"
  if [ "${USE_LOCAL_DATA}" = "1" ]; then
    log "config: LOCAL_BUNDLES_FILE=${LOCAL_BUNDLES_FILE} LOCAL_AUTHORED_JSON=${LOCAL_AUTHORED_JSON}"
  fi
  ensure_stack_up
  wipe_gics_to_seed
  if [ "${USE_LOCAL_DATA}" = "1" ]; then
    truncate_gics_consents
    CURRENT_CONSENTS=0
    log "CURRENT_CONSENTS reset to 0 (consents come from LOCAL_AUTHORED_JSON)"
  fi
  record_host
  if [ "${REUSE_STATE}" = "1" ]; then
    log "REUSE_STATE=1 — reusing existing cd-hds data, skipping test-data download + upload"
  else
    ensure_test_data "${MAX_N}"
    ensure_cd_hds_loaded "${MAX_N}"
  fi
  for n in "${SIZE_ARR[@]}"; do
    ensure_consents_for "${n}"
    for r in $(seq 1 "${RUNS}"); do
      run_one "${n}" "${r}"
    done
  done
  log "sweep complete — outputs in ${OUT_DIR}"
}

main "$@"
