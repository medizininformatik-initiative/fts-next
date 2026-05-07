#!/bin/bash
# Samples `docker stats` continuously and appends one CSV row per container per tick.
# Intended to run in the background during an E2E transfer to profile agent resource use.
#
# Usage:
#   ./measure-resources.sh OUTPUT_CSV [INTERVAL_SECONDS] [PROJECT_NAME]
#
# Defaults: INTERVAL_SECONDS=5, PROJECT_NAME=fts-test (matches .github/test/compose.yaml).
#
# CSV columns: timestamp,container,cpu_perc,mem_used_bytes,mem_limit_bytes,mem_perc,net_rx_bytes,net_tx_bytes,block_r_bytes,block_w_bytes,pids
#
# Example:
#   ./measure-resources.sh stats.csv 2 &
#   SAMPLER_PID=$!
#   make transfer-all wait
#   kill "${SAMPLER_PID}"
set -euo pipefail

if [ -z "${1:-}" ]; then
  >&2 echo "OUTPUT_CSV must be passed as first argument"
  exit 2
fi

OUTPUT="${1}"
INTERVAL="${2:-5}"
PROJECT="${3:-fts-test}"

# Convert docker's human-readable sizes (e.g. "1.23GiB", "456MB") to bytes.
to_bytes() {
  awk -v v="$1" 'BEGIN {
    n = v + 0
    u = v; sub(/^[0-9.]+/, "", u)
    mult = 1
    if (u == "B"   || u == "")    mult = 1
    else if (u == "kB" || u == "KB")  mult = 1000
    else if (u == "KiB")              mult = 1024
    else if (u == "MB")               mult = 1000*1000
    else if (u == "MiB")              mult = 1024*1024
    else if (u == "GB")               mult = 1000*1000*1000
    else if (u == "GiB")              mult = 1024*1024*1024
    else if (u == "TB")               mult = 1000*1000*1000*1000
    else if (u == "TiB")              mult = 1024*1024*1024*1024
    printf "%.0f", n * mult
  }'
}

# Emit header only if file does not yet exist — allows appending across multiple runs.
if [ ! -f "${OUTPUT}" ]; then
  echo "timestamp,container,cpu_perc,mem_used_bytes,mem_limit_bytes,mem_perc,net_rx_bytes,net_tx_bytes,block_r_bytes,block_w_bytes,pids" > "${OUTPUT}"
fi

cleanup() { echo "measure-resources: stopping (samples in ${OUTPUT})"; exit 0; }
trap cleanup INT TERM

echo "measure-resources: sampling project=${PROJECT} every ${INTERVAL}s into ${OUTPUT}"

FMT='{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}\t{{.PIDs}}'

while true; do
  ts="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  # --no-stream = single snapshot; filter by compose project label.
  docker stats --no-stream --format "${FMT}" \
    $(docker ps -q --filter "label=com.docker.compose.project=${PROJECT}") \
    | while IFS=$'\t' read -r name cpu mem memperc netio blockio pids; do
        [ -z "${name}" ] && continue
        mem_used="${mem%% / *}"
        mem_lim="${mem##* / }"
        net_rx="${netio%% / *}"
        net_tx="${netio##* / }"
        blk_r="${blockio%% / *}"
        blk_w="${blockio##* / }"
        printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n' \
          "${ts}" "${name}" "${cpu%\%}" \
          "$(to_bytes "${mem_used}")" "$(to_bytes "${mem_lim}")" "${memperc%\%}" \
          "$(to_bytes "${net_rx}")" "$(to_bytes "${net_tx}")" \
          "$(to_bytes "${blk_r}")" "$(to_bytes "${blk_w}")" \
          "${pids}" >> "${OUTPUT}"
      done
  sleep "${INTERVAL}"
done
