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

assert-empty() {
    if [ -z "$2" ]; then
        echo -e "  OK ✅  $1"
    else
        echo -e "Fail ❌  $1: $2"
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

# Fetch all resources upfront from both domains
cd_patients=$(fetch_bundle "$cd_hds_base_url" "Patient")
rd_patients=$(fetch_bundle "$rd_hds_base_url" "Patient")

declare -A cd_bundles rd_bundles
for resource_type in Encounter Observation Condition; do
    cd_bundles[$resource_type]=$(fetch_bundle "$cd_hds_base_url" "$resource_type")
    rd_bundles[$resource_type]=$(fetch_bundle "$rd_hds_base_url" "$resource_type")
done

rd_patient_count=$(echo "$rd_patients" | jq -r '.total')
assert-ge "rd-hds patient count" "$rd_patient_count" 1

# --- 1. Patient identifier values not from cd-hds ---
original_identifier_values=$(echo "$cd_patients" |
    jq -r '.entry[].resource.identifier[].value')
rd_identifier_values=$(echo "$rd_patients" |
    jq -r '.entry[].resource.identifier[].value')

leaked_identifiers=$(comm -12 \
    <(echo "$original_identifier_values" | sort) \
    <(echo "$rd_identifier_values" | sort) || true)

assert-empty "no original Patient identifiers in rd-hds" "$leaked_identifiers"

# --- 2. Patient resource IDs not from cd-hds ---
original_patient_ids=$(echo "$cd_patients" | jq -r '.entry[].resource.id')
rd_patient_ids=$(echo "$rd_patients" | jq -r '.entry[].resource.id')

leaked_ids=$(comm -12 \
    <(echo "$original_patient_ids" | sort) \
    <(echo "$rd_patient_ids" | sort) || true)

assert-empty "no original Patient IDs in rd-hds" "$leaked_ids"

# --- 3. Patient names are PSEUDONYMISIERT ---
non_pseudo_names=$(echo "$rd_patients" |
    jq -r '.entry[].resource.name[]? | (.family // empty), (.given[]? // empty)' |
    grep -v '^PSEUDONYMISIERT$' || true)

assert-empty "all Patient names are PSEUDONYMISIERT" "$non_pseudo_names"

# --- 4. No original Patient IDs in references ---
for resource_type in Encounter Observation Condition; do
    refs=$(echo "${rd_bundles[$resource_type]}" |
        jq -r '.entry[].resource | (.subject.reference // empty), (.encounter.reference // empty)')

    leaked_refs=$(echo "$refs" | grep -F -f <(echo "$original_patient_ids") || true)
    assert-empty "no original Patient IDs in ${resource_type} references" "$leaked_refs"
done

# --- 5. Resource IDs not from cd-hds ---
for resource_type in Encounter Observation Condition; do
    cd_ids=$(echo "${cd_bundles[$resource_type]}" | jq -r '.entry[].resource.id')
    rd_ids=$(echo "${rd_bundles[$resource_type]}" | jq -r '.entry[].resource.id')

    leaked_ids=$(comm -12 \
        <(echo "$cd_ids" | sort) \
        <(echo "$rd_ids" | sort) || true)

    assert-empty "no original ${resource_type} IDs in rd-hds" "$leaked_ids"
done

echo "Check Structural Invariants"

# --- 6. Each Patient has a unique identifier (1:1 mapping, no collisions) ---
identifier_count=$(echo "$rd_patients" |
    jq -r '[.entry[].resource.identifier[].value] | length')
distinct_identifier_count=$(echo "$rd_patients" |
    jq -r '[.entry[].resource.identifier[].value] | unique | length')

assert "all Patient identifiers are unique" "$distinct_identifier_count" "$identifier_count"

# --- 7. Referential integrity: subject references resolve to actual Patients ---
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
