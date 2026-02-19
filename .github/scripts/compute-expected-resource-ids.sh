#!/bin/bash
set -euo pipefail

# Compute expected pseudonyms and resource IDs by querying CD-HDS and gPAS.
#
# Resource IDs in RD-HDS are SHA-256(salt + namespacedKey) where:
#   - salt = gPAS pseudonym for "Salt_{patientIdentifier}" in domain "MII"
#   - namespacedKey = "{patientIdentifier}.{ResourceType}:{blazeResourceId}"
#
# Patient pseudonyms are gPAS pseudonyms for the patient identifier in domain "MII".
#
# Outputs JSON: {"pseudonyms": [...], "resourceIds": [...]}
#
# Usage: compute-expected-resource-ids.sh <cd-hds-base-url> <gpas-base-url>

cd_hds_base_url=$1
gpas_base_url=$2

PAGE_SIZE=10000
DOMAIN="MII"
RESOURCE_TYPES=(Encounter Observation Condition DiagnosticReport MedicationAdministration)

# Fetch all FHIR bundle entries across pages into a temp file (one JSON object per line).
fetch_all_entries() {
    local base_url=$1
    local url=$2
    local out_file=$3
    local origin
    origin=$(echo "$base_url" | sed 's|^\(http://[^/]*\).*|\1|')

    while [ -n "$url" ]; do
        local page
        page=$(curl -sSf "$url")
        echo "$page" | jq -c '.entry[]?' >> "$out_file"

        local next_url
        next_url=$(echo "$page" | jq -r '(.link[]? | select(.relation == "next") | .url) // empty')
        if [ -n "$next_url" ]; then
            url=$(echo "$next_url" | sed "s|^http://[^/]*|${origin}|")
        else
            url=""
        fi
    done
}

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

pseudonyms_file="$tmp_dir/pseudonyms"
resource_ids_file="$tmp_dir/resource_ids"

# --- 1. Fetch all patients from CD-HDS ---
patient_entries_file="$tmp_dir/patient_entries"
fetch_all_entries "$cd_hds_base_url" \
    "${cd_hds_base_url}/Patient?_count=${PAGE_SIZE}&_elements=id,identifier" \
    "$patient_entries_file"

# Extract blaze_id<TAB>pid pairs (first identifier with system http://fts.smith.care)
patient_pids_file="$tmp_dir/patient_pids"
jq -r '.resource | .id as $id |
    (.identifier[]? | select(.system == "http://fts.smith.care") | .value) as $pid |
    "\($id)\t\($pid)"' "$patient_entries_file" | sort -u -t$'\t' -k1,1 > "$patient_pids_file"


# --- 2. Batch-fetch all pseudonyms and salts from gPAS ---
# Build the list of originals: each pid and its Salt_ variant
gpas_originals=()
while IFS=$'\t' read -r _ pid; do
    gpas_originals+=("$pid" "Salt_${pid}")
done < "$patient_pids_file"

# Build FHIR Parameters request body
gpas_body=$(printf '%s\n' "${gpas_originals[@]}" | jq -R -s --arg domain "$DOMAIN" '
    split("\n") | map(select(length > 0)) |
    {
        resourceType: "Parameters",
        parameter: ([{ name: "target", valueString: $domain }] +
            map({ name: "original", valueString: . }))
    }')

gpas_response=$(curl -sSf \
    -H "Content-Type: application/fhir+json" \
    -H "Accept: application/fhir+json" \
    -d "$gpas_body" \
    "${gpas_base_url}/\$pseudonymizeAllowCreate")

# Parse response into TSV: original<TAB>pseudonym
gpas_mapping_file="$tmp_dir/gpas_mapping"
echo "$gpas_response" | jq -r '
    .parameter[]? | .part as $parts |
    ($parts | map(select(.name == "original")) | first | .valueIdentifier.value) as $orig |
    ($parts | map(select(.name == "pseudonym")) | first | .valueIdentifier.value) as $pseudo |
    "\($orig)\t\($pseudo)"' > "$gpas_mapping_file"

# Load into associative array
declare -A gpas_mapping
while IFS=$'\t' read -r orig pseudo; do
    gpas_mapping["$orig"]="$pseudo"
done < "$gpas_mapping_file"


# --- 3. Compute patient pseudonyms and Patient resource IDs ---
declare -A patient_data_pid patient_data_salt

while IFS=$'\t' read -r blaze_id pid; do
    patient_pseudo="${gpas_mapping[$pid]:-}"
    salt="${gpas_mapping[Salt_${pid}]:-}"

    if [ -z "$patient_pseudo" ] || [ -z "$salt" ]; then
        >&2 echo "WARNING: missing gPAS mapping for patient $pid"
        continue
    fi

    echo "$patient_pseudo" >> "$pseudonyms_file"
    patient_data_pid["$blaze_id"]="$pid"
    patient_data_salt["$blaze_id"]="$salt"

    namespaced_key="${pid}.Patient:${blaze_id}"
    printf '%s' "${salt}${namespaced_key}" | sha256sum | cut -d' ' -f1 >> "$resource_ids_file"
done < "$patient_pids_file"


# --- 4. Fetch resource types and compute their resource IDs ---
for res_type in "${RESOURCE_TYPES[@]}"; do
    entries_file="$tmp_dir/entries_${res_type}"
    fetch_all_entries "$cd_hds_base_url" \
        "${cd_hds_base_url}/${res_type}?_count=${PAGE_SIZE}&_elements=id,subject" \
        "$entries_file"

    # Extract res_id and patient blaze_id from subject reference
    while IFS=$'\t' read -r res_id patient_blaze_id; do
        pid="${patient_data_pid[$patient_blaze_id]:-}"
        salt="${patient_data_salt[$patient_blaze_id]:-}"

        if [ -z "$pid" ] || [ -z "$salt" ] || [ -z "$res_id" ]; then
            continue
        fi

        namespaced_key="${pid}.${res_type}:${res_id}"
        printf '%s' "${salt}${namespaced_key}" | sha256sum | cut -d' ' -f1
    done < <(jq -r '.resource |
        .id as $id |
        (.subject.reference // "" | ltrimstr("Patient/")) as $patient_id |
        "\($id)\t\($patient_id)"' "$entries_file") >> "$resource_ids_file"
done

# --- Output JSON ---
jq -R -s 'split("\n") | map(select(length > 0))' "$pseudonyms_file" > "$tmp_dir/pseudonyms.json"
jq -R -s 'split("\n") | map(select(length > 0))' "$resource_ids_file" > "$tmp_dir/resource_ids.json"
jq -n \
    --slurpfile pseudonyms "$tmp_dir/pseudonyms.json" \
    --slurpfile resourceIds "$tmp_dir/resource_ids.json" \
    '{ pseudonyms: $pseudonyms[0], resourceIds: $resourceIds[0] }'
