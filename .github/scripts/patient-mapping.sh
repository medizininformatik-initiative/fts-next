#!/bin/bash
# Shared helpers that map CD-HDS patients and resources onto their RD-HDS
# counterparts. Source this file, do not execute it.
#
# RD-HDS resource IDs are SHA-256(salt + namespacedKey) where:
#   - salt = gPAS pseudonym for "Salt_{patientIdentifier}" in domain "MII"
#   - namespacedKey = "{patientIdentifier}.{ResourceType}:{cdResourceId}"
#
# Patient pseudonyms are gPAS pseudonyms for the patient identifier in domain "MII".

FTS_DOMAIN=${FTS_DOMAIN:-MII}
FTS_PAGE_SIZE=${FTS_PAGE_SIZE:-10000}
FTS_IDENTIFIER_SYSTEM=${FTS_IDENTIFIER_SYSTEM:-http://fts.smith.care}

# Keyed by the CD-HDS Patient resource ID. Filled by load_patient_mappings.
# shellcheck disable=SC2034
declare -gA PATIENT_PID PATIENT_SALT PATIENT_PSEUDONYM

# Fetch all FHIR bundle entries across pages into a file, one JSON object per line.
#
# Usage: fetch_all_entries <base-url> <start-url> <out-file>
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
            # The FHIR server's "next" links use the container-internal hostname,
            # which is unreachable from the host. Rewrite it to the mapped origin.
            url=$(echo "$next_url" | sed "s|^http://[^/]*|${origin}|")
        else
            url=""
        fi
    done
}

# Load the gPAS pseudonym and the date/ID salt of every CD-HDS patient into
# PATIENT_PID, PATIENT_SALT and PATIENT_PSEUDONYM.
#
# Usage: load_patient_mappings <cd-hds-base-url> <gpas-base-url>
load_patient_mappings() {
    local cd_hds_base_url=$1
    local gpas_base_url=$2

    local tmp_dir
    tmp_dir=$(mktemp -d)

    local patient_entries_file="$tmp_dir/patient_entries"
    fetch_all_entries "$cd_hds_base_url" \
        "${cd_hds_base_url}/Patient?_count=${FTS_PAGE_SIZE}&_elements=id,identifier" \
        "$patient_entries_file"

    # Extract cd_id<TAB>pid pairs (first identifier with the FTS system)
    local patient_pids_file="$tmp_dir/patient_pids"
    jq -r --arg system "$FTS_IDENTIFIER_SYSTEM" '.resource | .id as $id |
        (.identifier[]? | select(.system == $system) | .value) as $pid |
        "\($id)\t\($pid)"' "$patient_entries_file" | sort -u -t$'\t' -k1,1 > "$patient_pids_file"

    # Build the list of originals: each pid and its Salt_ variant
    local gpas_originals=()
    local cd_id pid
    while IFS=$'\t' read -r _ pid; do
        gpas_originals+=("$pid" "Salt_${pid}")
    done < "$patient_pids_file"

    local gpas_body
    gpas_body=$(printf '%s\n' "${gpas_originals[@]}" | jq -R -s --arg domain "$FTS_DOMAIN" '
        split("\n") | map(select(length > 0)) |
        {
            resourceType: "Parameters",
            parameter: ([{ name: "target", valueString: $domain }] +
                map({ name: "original", valueString: . }))
        }')

    local gpas_response
    gpas_response=$(curl -sSf \
        -H "Content-Type: application/fhir+json" \
        -H "Accept: application/fhir+json" \
        -d "$gpas_body" \
        "${gpas_base_url}/\$pseudonymizeAllowCreate")

    local gpas_mapping_file="$tmp_dir/gpas_mapping"
    echo "$gpas_response" | jq -r '
        .parameter[]? | .part as $parts |
        ($parts | map(select(.name == "original")) | first | .valueIdentifier.value) as $orig |
        ($parts | map(select(.name == "pseudonym")) | first | .valueIdentifier.value) as $pseudo |
        "\($orig)\t\($pseudo)"' > "$gpas_mapping_file"

    local -A gpas_mapping
    local orig pseudo
    while IFS=$'\t' read -r orig pseudo; do
        gpas_mapping["$orig"]="$pseudo"
    done < "$gpas_mapping_file"

    while IFS=$'\t' read -r cd_id pid; do
        local patient_pseudo="${gpas_mapping[$pid]:-}"
        local salt="${gpas_mapping[Salt_${pid}]:-}"

        if [ -z "$patient_pseudo" ] || [ -z "$salt" ]; then
            >&2 echo "WARNING: missing gPAS mapping for patient $pid"
            continue
        fi

        PATIENT_PID["$cd_id"]="$pid"
        PATIENT_SALT["$cd_id"]="$salt"
        PATIENT_PSEUDONYM["$cd_id"]="$patient_pseudo"
    done < "$patient_pids_file"

    rm -rf "$tmp_dir"
}

# Print the RD-HDS resource ID of a CD-HDS resource.
#
# Usage: rd_resource_id <salt> <pid> <resource-type> <cd-resource-id>
rd_resource_id() {
    local salt=$1 pid=$2 resource_type=$3 cd_id=$4
    printf '%s' "${salt}${pid}.${resource_type}:${cd_id}" | sha256sum | cut -d' ' -f1
}
