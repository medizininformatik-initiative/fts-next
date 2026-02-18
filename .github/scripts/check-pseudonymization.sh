#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
. "$SCRIPT_DIR/util.sh"

if ! cd_hds_base_url="http://$(docker compose port cd-hds 8080)/fhir"; then
    >&2 echo "Unable to find clinical domain health data store URL"
    exit 2
fi

if ! rd_hds_base_url="http://$(docker compose port rd-hds 8080)/fhir"; then
    >&2 echo "Unable to find research domain health data store URL"
    exit 2
fi

if ! gpas_base_url="http://$(docker compose port gpas 8080)/ttp-fhir/fhir/gpas"; then
    >&2 echo "Unable to find gPAS URL"
    exit 2
fi

assert-empty() {
    if [ -z "$2" ]; then
        echo -e "  OK ✅  $1"
    else
        echo -e "Fail ❌  $1: $2"
        exit 1
    fi
}

# Asserts that every line in 'actual' appears in 'expected' (actual ⊆ expected).
assert-subset() {
    local label=$1
    local actual=$2
    local expected=$3

    local unexpected
    unexpected=$(comm -23 <(echo "$actual") <(echo "$expected") || true)
    if [ -z "$unexpected" ]; then
        local count
        count=$(echo "$actual" | grep -c . || true)
        echo -e "  OK ✅  $label ($count values)"
    else
        local count
        count=$(echo "$unexpected" | wc -l)
        echo -e "Fail ❌  $label: $count unexpected value(s):"
        echo "$unexpected" | head -10
        exit 1
    fi
}

# Fetches all resources across pages. Follows FHIR Bundle "next" links until
# exhausted, then verifies the accumulated count matches the reported total.
fetch_bundle() {
    local base_url=$1
    local resource_type=$2
    local url="${base_url}/${resource_type}?_count=500"
    local total=0

    # The FHIR server's "next" links use the container-internal hostname
    # (e.g. http://cd-hds:8080/...), which is unreachable from the host.
    # We replace that origin with the one from docker compose port.
    local origin
    origin=$(echo "$base_url" | sed 's|^\(http://[^/]*\).*|\1|')

    # Accumulate entries in a temp file to avoid ARG_MAX limits on large datasets.
    # Each page appends a JSON array; jq merges them at the end.
    local entries_file
    entries_file=$(mktemp)

    while [ -n "$url" ]; do
        local page
        page=$(curl -sSf "$url")

        # Capture total from first response (later pages may omit it)
        if [ ! -s "$entries_file" ]; then
            total=$(echo "$page" | jq -r '.total // 0')
        fi

        echo "$page" | jq -c '[.entry[]?]' >> "$entries_file"

        local next_url
        next_url=$(echo "$page" | jq -r '(.link[]? | select(.relation == "next") | .url) // empty')
        # Rewrite container-internal origin to the host-accessible one
        url=$(echo "$next_url" | sed "s|^http://[^/]*|${origin}|")
    done

    local entry_count
    entry_count=$(jq -s 'add // [] | length' "$entries_file")
    if [ "$entry_count" != "$total" ]; then
        rm -f "$entries_file"
        >&2 echo "FATAL: fetched ${entry_count}/${total} ${resource_type} resources — paging may be broken"
        exit 1
    fi

    jq -s "{total: $total, entry: (add // [])}" "$entries_file"
    rm -f "$entries_file"
}

echo "Check Pseudonymization"

# Start computing expected pseudonyms and resource IDs from gPAS in background
expected_json_file=$(mktemp)
bash "$SCRIPT_DIR/compute-expected-resource-ids.sh" \
    "$cd_hds_base_url" "$gpas_base_url" > "$expected_json_file" &
bg_pid=$!

# Fetch all resources from the research domain
rd_patients=$(fetch_bundle "$rd_hds_base_url" "Patient")

declare -A rd_bundles
for resource_type in Encounter Observation Condition; do
    rd_bundles[$resource_type]=$(fetch_bundle "$rd_hds_base_url" "$resource_type")
done

rd_patient_count=$(echo "$rd_patients" | jq -r '.total')
assert-ge "rd-hds patient count" "$rd_patient_count" 1

# --- 1. Patient names are PSEUDONYMISIERT ---
non_pseudo_names=$(echo "$rd_patients" |
    jq -r '.entry[].resource.name[]? | (.family // empty), (.given[]? // empty)' |
    grep -v '^PSEUDONYMISIERT$' || true)

assert-empty "all Patient names are PSEUDONYMISIERT" "$non_pseudo_names"

echo "Check Structural Invariants"

# --- 2. Each Patient has a unique identifier (1:1 mapping, no collisions) ---
identifier_count=$(echo "$rd_patients" |
    jq -r '[.entry[].resource.identifier[].value] | length')
distinct_identifier_count=$(echo "$rd_patients" |
    jq -r '[.entry[].resource.identifier[].value] | unique | length')

assert "all Patient identifiers are unique" "$distinct_identifier_count" "$identifier_count"

# --- 3. Referential integrity: subject references resolve to actual Patients ---
rd_patient_ids=$(echo "$rd_patients" | jq -r '.entry[].resource.id')
rd_patient_id_set=$(echo "$rd_patient_ids" | sort -u)

for resource_type in Encounter Observation Condition; do
    referenced_patient_ids=$(echo "${rd_bundles[$resource_type]}" |
        jq -r '.entry[].resource.subject.reference // empty' |
        sed 's|Patient/||' |
        sort -u)

    unresolved=$(comm -23 \
        <(echo "$referenced_patient_ids") \
        <(echo "$rd_patient_id_set") || true)

    assert-empty "all ${resource_type} subject references resolve to rd-hds Patients" "$unresolved"
done

echo "Check Deterministic Pseudonyms"

# Wait for background gPAS computation to finish
wait "$bg_pid"
expected_json=$(cat "$expected_json_file")
rm -f "$expected_json_file"

expected_pseudonyms=$(echo "$expected_json" | jq -r '.pseudonyms[]' | sort -u)
expected_resource_ids=$(echo "$expected_json" | jq -r '.resourceIds[]' | sort -u)

# --- 4. Patient identifier pseudonyms match gPAS ---
rd_pseudonyms=$(echo "$rd_patients" |
    jq -r '.entry[].resource.identifier[]? | select(.system == "http://fts.smith.care") | .value' |
    sort -u)

assert-subset "patient identifier pseudonyms match gPAS" "$rd_pseudonyms" "$expected_pseudonyms"

# --- 5. Resource IDs match SHA-256(salt + namespacedKey) ---
# Collect all resource IDs from RD-HDS (Patient + all resource types)
all_rd_resource_ids=$(echo "$rd_patients" | jq -r '.entry[].resource.id')
for resource_type in Encounter Observation Condition; do
    all_rd_resource_ids+=$'\n'$(echo "${rd_bundles[$resource_type]}" | jq -r '.entry[].resource.id')
done
for resource_type in DiagnosticReport MedicationAdministration; do
    extra_bundle=$(fetch_bundle "$rd_hds_base_url" "$resource_type")
    all_rd_resource_ids+=$'\n'$(echo "$extra_bundle" | jq -r '.entry[].resource.id')
done
all_rd_resource_ids=$(echo "$all_rd_resource_ids" | grep -v '^$' | sort -u)

assert-subset "resource IDs match expected SHA-256 hashes" "$all_rd_resource_ids" "$expected_resource_ids"
