#!/bin/bash
set -euo pipefail

# Compute expected pseudonyms and resource IDs by querying CD-HDS and gPAS.
# See patient-mapping.sh for how the IDs are derived.
#
# Outputs JSON: {"pseudonyms": [...], "resourceIds": [...]}
#
# Usage: compute-expected-resource-ids.sh <cd-hds-base-url> <gpas-base-url>

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
# shellcheck source=.github/scripts/patient-mapping.sh
. "$SCRIPT_DIR/patient-mapping.sh"

cd_hds_base_url=$1
gpas_base_url=$2

RESOURCE_TYPES=(Encounter Observation Condition DiagnosticReport MedicationAdministration)

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

pseudonyms_file="$tmp_dir/pseudonyms"
resource_ids_file="$tmp_dir/resource_ids"

# --- 1. Fetch patients from CD-HDS and their gPAS pseudonyms and salts ---
load_patient_mappings "$cd_hds_base_url" "$gpas_base_url"

# --- 2. Compute patient pseudonyms and Patient resource IDs ---
for cd_id in "${!PATIENT_PID[@]}"; do
    pid=${PATIENT_PID[$cd_id]}
    salt=${PATIENT_SALT[$cd_id]}

    echo "${PATIENT_PSEUDONYM[$cd_id]}" >> "$pseudonyms_file"
    rd_resource_id "$salt" "$pid" Patient "$cd_id" >> "$resource_ids_file"
done

# --- 3. Fetch resource types and compute their resource IDs ---
for res_type in "${RESOURCE_TYPES[@]}"; do
    entries_file="$tmp_dir/entries_${res_type}"
    fetch_all_entries "$cd_hds_base_url" \
        "${cd_hds_base_url}/${res_type}?_count=${FTS_PAGE_SIZE}&_elements=id,subject" \
        "$entries_file"

    # Extract res_id and the CD Patient ID from the subject reference
    while IFS=$'\t' read -r res_id patient_cd_id; do
        pid="${PATIENT_PID[$patient_cd_id]:-}"
        salt="${PATIENT_SALT[$patient_cd_id]:-}"

        if [ -z "$pid" ] || [ -z "$salt" ] || [ -z "$res_id" ]; then
            continue
        fi

        rd_resource_id "$salt" "$pid" "$res_type" "$res_id"
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
