#!/bin/bash
set -euo pipefail

# Verify that the deidentifhir shiftDateHandler shifted the dates of the
# transferred resources.
#
# The clinical domain agent replaces each configured date with a transport ID,
# the trust center agent maps that ID onto a shifted date, and the research
# domain agent writes the shifted date into RD-HDS. The shift is deterministic
# per patient, so all dates of one patient move by the same amount.
#
# The check runs in three steps:
#   1. Find the patients that a transfer moved into RD-HDS. CD-HDS also holds
#      patients that no transfer selected. A patient counts as transferred if
#      RD-HDS contains the Patient resource ID derived from it.
#   2. Pair each original date of those patients with the date of the same
#      field in the RD-HDS counterpart of the resource.
#   3. Assert that the pairs cover every date field, at least two patients, and
#      at least two dates per patient. Without that coverage the checks below
#      hold for an empty or one-sided set of pairs.
#   4. Compute the shift of every pair and assert that no date is unshifted,
#      no shift exceeds maxDateShift, each patient has exactly one shift, and
#      the shifts differ between patients.
#
# Usage: check-date-shift.sh [project]
#
# With a project name, the maximum shift comes from that project configuration.
# Without one, it comes from the largest maxDateShift of all project files.

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
# shellcheck source=.github/scripts/patient-mapping.sh
. "$SCRIPT_DIR/patient-mapping.sh"

PROJECT_DIR=${PROJECT_DIR:-cd-agent/projects}
# Number of resources examined per patient and resource type.
PER_PATIENT_LIMIT=${PER_PATIENT_LIMIT:-5}
# The consistency check needs this many dates per patient to have any force.
MIN_DATES_PER_PATIENT=${MIN_DATES_PER_PATIENT:-2}

# Date fields that the deidentifhir configuration shifts, per resource type.
declare -A DATE_FIELDS=(
    [Encounter]="period.start period.end"
    [Condition]="recordedDate"
)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

# Convert an ISO-8601 duration such as P14D or PT12H30M into seconds.
iso_duration_to_seconds() {
    local duration=$1
    local pattern='^P(([0-9]+)D)?(T(([0-9]+)H)?(([0-9]+)M)?(([0-9]+)S)?)?$'
    if [[ ! $duration =~ $pattern ]]; then
        >&2 echo "Cannot read the duration $duration"
        exit 2
    fi
    local days=${BASH_REMATCH[2]:-0} hours=${BASH_REMATCH[5]:-0}
    local minutes=${BASH_REMATCH[7]:-0} seconds=${BASH_REMATCH[9]:-0}
    echo $(( days * 86400 + hours * 3600 + minutes * 60 + seconds ))
}

# Print the largest maxDateShift of the given project files, in seconds.
max_date_shift_seconds() {
    local max=0 duration seconds
    while read -r duration; do
        seconds=$(iso_duration_to_seconds "$duration")
        [ "$seconds" -gt "$max" ] && max=$seconds
    done < <(grep -ho 'maxDateShift:[[:space:]]*[^[:space:]]*' "$@" | awk '{print $2}' | sort -u)

    if [ "$max" -eq 0 ]; then
        >&2 echo "No maxDateShift found in $*"
        exit 2
    fi
    echo "$max"
}

if [ -n "${MAX_DATE_SHIFT_SECONDS:-}" ]; then
    :
elif [ -n "${1:-}" ]; then
    MAX_DATE_SHIFT_SECONDS=$(max_date_shift_seconds "${PROJECT_DIR}/${1}.yaml")
else
    MAX_DATE_SHIFT_SECONDS=$(max_date_shift_seconds "${PROJECT_DIR}"/*.yaml)
fi

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

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

# ---------------------------------------------------------------------------
# Reading dates from a health data store
# ---------------------------------------------------------------------------

# Print one line per resource and date field of one resource type:
#
#   resource-id <TAB> patient-id <TAB> field <TAB> date
#
# A missing date prints as "-", so every resource has a line for every field.
#
# Usage: fetch_dates <base-url> <resource-type> <field>...
fetch_dates() {
    local base_url=$1 res_type=$2
    shift 2
    local fields=("$@")

    # _elements takes top-level element names only, e.g. "period" for "period.start".
    local elements
    elements=$(printf '%s\n' "${fields[@]%%.*}" | sort -u | paste -sd, -)

    local entries_file
    entries_file=$(mktemp -p "$tmp_dir")
    fetch_all_entries "$base_url" \
        "${base_url}/${res_type}?_count=${FTS_PAGE_SIZE}&_elements=id,subject,${elements}" \
        "$entries_file"

    jq -r '
        .resource
        | .id as $id
        | (.subject.reference // "-" | ltrimstr("Patient/")) as $patient
        | $ARGS.positional[] as $field
        | [$id, $patient, $field, (getpath($field | split(".")) // "-")]
        | @tsv' "$entries_file" --args "${fields[@]}"
}

# Load the output of fetch_dates into two associative arrays:
#   dates[resource-id/field] = date
#   patient_of[resource-id]  = patient-id
#
# Usage: load_dates <base-url> <resource-type> <dates-array> <patient-array> <field>...
# shellcheck disable=SC2034  # the arrays are filled through name references
load_dates() {
    local base_url=$1 res_type=$2
    local -n dates=$3 patient_of=$4
    shift 4

    local resource patient field date
    while IFS=$'\t' read -r resource patient field date; do
        dates["$resource/$field"]=$date
        patient_of["$resource"]=$patient
    done < <(fetch_dates "$base_url" "$res_type" "$@")
}

# ---------------------------------------------------------------------------
# Step 1: transferred patients
# ---------------------------------------------------------------------------

# Keyed by the CD-HDS Patient resource ID.
declare -A TRANSFERRED

find_transferred_patients() {
    local -A rd_patient_ids=()
    local rd_id
    while read -r rd_id; do
        rd_patient_ids["$rd_id"]=1
    done < <(fetch_dates "$rd_hds_base_url" Patient id | cut -f1)

    local cd_id
    for cd_id in "${!PATIENT_PID[@]}"; do
        rd_id=$(rd_resource_id "${PATIENT_SALT[$cd_id]}" "${PATIENT_PID[$cd_id]}" Patient "$cd_id")
        if [ -n "${rd_patient_ids[$rd_id]:-}" ]; then
            TRANSFERRED["$cd_id"]=1
        fi
    done
}

# ---------------------------------------------------------------------------
# Step 2: pairs of original and shifted dates
# ---------------------------------------------------------------------------

# Print one line per shifted date of one resource type:
#
#   pseudonym <TAB> ResourceType.field <TAB> original-date <TAB> shifted-date
#
# Usage: collect_date_pairs <resource-type>
collect_date_pairs() {
    local res_type=$1
    local fields
    read -r -a fields <<< "${DATE_FIELDS[$res_type]}"

    local -A cd_date=() cd_patient=() rd_date=() rd_patient=()
    load_dates "$cd_hds_base_url" "$res_type" cd_date cd_patient "${fields[@]}"
    load_dates "$rd_hds_base_url" "$res_type" rd_date rd_patient "${fields[@]}"

    local -A examined=()
    local cd_id patient pid rd_id field
    for cd_id in "${!cd_patient[@]}"; do
        patient=${cd_patient[$cd_id]}
        [ -n "${TRANSFERRED[$patient]:-}" ] || continue

        examined["$patient"]=$(( ${examined[$patient]:-0} + 1 ))
        [ "${examined[$patient]}" -le "$PER_PATIENT_LIMIT" ] || continue

        pid=${PATIENT_PID[$patient]}
        rd_id=$(rd_resource_id "${PATIENT_SALT[$patient]}" "$pid" "$res_type" "$cd_id")
        # A resource without counterpart did not match any deidentifhir profile.
        [ -n "${rd_patient[$rd_id]:-}" ] || continue

        for field in "${fields[@]}"; do
            [ "${cd_date["$cd_id/$field"]}" != "-" ] || continue
            printf '%s\t%s.%s\t%s\t%s\n' "$pid" "$res_type" "$field" \
                "${cd_date["$cd_id/$field"]}" "${rd_date["$rd_id/$field"]}"
        done
    done
}

# ---------------------------------------------------------------------------
# Steps 3 and 4: checks
# ---------------------------------------------------------------------------

FAILED=0

# Print "line: count" for each distinct input line.
count_lines() {
    sort | uniq -c | awk '{ print $2 ": " $1 }'
}

# Report a check. It passes when the list of violations is empty.
#
# Usage: check <label> <violations>
check() {
    local label=$1 violations=$2
    if [ -z "$violations" ]; then
        echo "  OK ✅  $label"
    else
        echo "Fail ❌  $label"
        local lines
        mapfile -t lines <<< "$violations"
        printf '         %s\n' "${lines[@]}"
        FAILED=1
    fi
}

echo "Check Date Shifting"

load_patient_mappings "$cd_hds_base_url" "$gpas_base_url"

find_transferred_patients
if [ ${#TRANSFERRED[@]} -lt 2 ]; then
    echo "Fail ❌  found ${#TRANSFERRED[@]} transferred patients, expected at least 2"
    exit 1
fi

pairs_file="$tmp_dir/pairs"
for res_type in "${!DATE_FIELDS[@]}"; do
    collect_date_pairs "$res_type"
done > "$pairs_file"

compared=$(wc -l < "$pairs_file")

# Coverage. Every check below reports success on an empty list of violations,
# so an empty or one-sided set of pairs passes them all. These three checks
# assert the structure that the later checks need.

# Each entry of DATE_FIELDS must contribute a pair. Otherwise the shift checks
# say nothing about that field.
expected_fields=$(
    for res_type in "${!DATE_FIELDS[@]}"; do
        read -r -a fields <<< "${DATE_FIELDS[$res_type]}"
        printf '%s\n' "${fields[@]/#/${res_type}.}"
    done | sort
)
missing_fields=$(comm -23 <(echo "$expected_fields") <(cut -f2 "$pairs_file" | sort -u))
check "every date field contributes a pair" "$missing_fields"

# The last check compares the shifts of two patients, so two patients must
# contribute. A patient can be in TRANSFERRED and still contribute no pair.
patients_with_pairs=$(cut -f1 "$pairs_file" | sort -u | wc -l)
if [ "$patients_with_pairs" -lt 2 ]; then
    check "at least two patients contribute pairs" \
        "$patients_with_pairs of ${#TRANSFERRED[@]} transferred patients contribute a pair"
else
    check "at least two patients contribute pairs ($patients_with_pairs patients)" ""
fi

# "each patient has one date shift" is vacuous for a patient with one pair.
too_few_dates=$(cut -f1 "$pairs_file" | sort | uniq -c \
    | awk -v min="$MIN_DATES_PER_PATIENT" \
        '$1 < min { print $2 " contributes " $1 " of " min " dates" }')
check "each patient contributes at least $MIN_DATES_PER_PATIENT dates" "$too_few_dates"

if [ "$FAILED" -ne 0 ]; then
    exit 1
fi

unrestored=$(awk -F'\t' '$4 == "-" { print $2 }' "$pairs_file" | count_lines)
if [ -n "$unrestored" ]; then
    check "RD-HDS holds every shifted date" "$unrestored"
    exit 1
fi

# pseudonym <TAB> ResourceType.field <TAB> shift in seconds
#
# GNU date reads a whole file at once, which keeps this at one process per
# column instead of one process per date.
shifts_file="$tmp_dir/shifts"
paste <(cut -f1,2 "$pairs_file") \
      <(cut -f3 "$pairs_file" | date -u -f - +%s) \
      <(cut -f4 "$pairs_file" | date -u -f - +%s) \
    | awk -F'\t' -v OFS='\t' '{ print $1, $2, $4 - $3 }' > "$shifts_file"

field_count=$(cut -f2 "$shifts_file" | sort -u | wc -l)
unshifted=$(awk -F'\t' '$3 == 0 { print $2 }' "$shifts_file" | count_lines)
check "all dates are shifted ($compared values in $field_count fields)" "$unshifted"

too_far=$(awk -F'\t' -v max="$MAX_DATE_SHIFT_SECONDS" \
    '$3 > max || $3 < -max { print $1 " is shifted by " $3 " seconds" }' "$shifts_file" | sort -u)
check "all shifts are within +/-$MAX_DATE_SHIFT_SECONDS seconds" "$too_far"

patient_count=$(cut -f1 "$shifts_file" | sort -u | wc -l)
# Patients that appear with more than one distinct shift.
inconsistent=$(cut -f1,3 "$shifts_file" | sort -u | cut -f1 | uniq -d)
check "each patient has one date shift ($patient_count patients)" "$inconsistent"

distinct_shifts=$(cut -f3 "$shifts_file" | sort -u | wc -l)
if [ "$distinct_shifts" -lt 2 ]; then
    check "date shifts differ per patient" "all patients share the same date shift"
else
    check "date shifts differ per patient ($distinct_shifts distinct shifts)" ""
fi

exit "$FAILED"
