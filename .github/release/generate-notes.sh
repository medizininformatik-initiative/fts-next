#!/bin/bash
set -euo pipefail

# Get release_name version from command line argument
release_name="${1}"

api_url="${MISTRAL_API_URL:-https://api.mistral.ai/v1/chat/completions}"
model="${MISTRAL_MODEL:-mistral-medium-latest}"
retry_delay="${NOTES_RETRY_DELAY:-5}"
retry_max_time="${NOTES_RETRY_MAX_TIME:-180}"

log() {
  printf '%s\n' "$*" >&2
}

# Surfaces a skipped generation in the workflow UI as well as the run log, so
# it is not discovered by noticing an empty release body.
annotate_skipped() {
  printf '::warning title=Release Notes Not Generated::%s\n' "$*" >&2
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "> [!WARNING]"
      echo "> $*"
    } >>"${GITHUB_STEP_SUMMARY}"
  fi
}

# Extract milestone name
# * remove leading v
# * remove prerelease identifiers
# * remove bugfix release identifier when '.0' release
milestone_name="$(echo "${release_name#v}" | cut -d- -f1 | sed 's/\.0$//')"

# Get milestone from GH API
gh extension install valeriobelli/gh-milestone &>/dev/null
milestone="$(gh milestone ls --query "${milestone_name}" --json number | jq -e '.[].number')"
last_release="$(gh release ls --limit 1 --exclude-drafts --json tagName | jq -er '.[].tagName')"

# Create prompt temp file from template
sed "s|{RELEASE_NAME}|${release_name}|" generate-notes.tmpl.md >prompt.md

# Gather Issues
gh issue ls --milestone "${milestone_name}" --state closed --json title,number,body,url | jq -e >issues.json
sed -i -e '/{ISSUES}/{e cat issues.json' -e ';d}' prompt.md

# Get the latest releases
{
  while read -r rel; do
    gh release view "${rel}" --json name,body
  done <<<"$(gh release ls --limit 10 --exclude-drafts --json tagName | jq -er '.[].tagName')"
} | jq -es >releases.json
sed -i -e '/{RELEASES}/{e cat releases.json' -e ';d}' prompt.md

# Post the prompt, writing the response body to $1 and echoing the HTTP status.
# Writing to a file rather than stdout matters: curl emits the body of every
# attempt, so a request that only succeeds on a retry would otherwise be
# preceded by the bodies of the attempts that failed. Transient failures are
# retried; a hard failure is reported as status 000 rather than aborting, so the
# caller decides whether a release can proceed without generated notes.
request_completion() {
  jq -n --rawfile content prompt.md --arg model "${model}" \
    '{model: $model, messages: [{role: "user", content: $content}]}' \
  | curl -sSL --connect-timeout 10 \
      --retry 5 --retry-all-errors \
      --retry-delay "${retry_delay}" --retry-max-time "${retry_max_time}" \
      -o "$1" -w '%{http_code}' \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer ${MISTRAL_API_KEY}" \
      --data-binary @- \
      "${api_url}" || true
}

# Echoes the generated notes body, or returns non-zero having explained why it
# could not be produced.
generate_notes_body() {
  local http_status content

  http_status="$(request_completion response.json)"

  if [ "${http_status}" != "200" ]; then
    log "Release notes service returned HTTP ${http_status}."
    log "Response: $(cat response.json 2>/dev/null)"
    return 1
  fi

  content="$(jq -r '.choices[0].message.content // empty' <response.json 2>/dev/null || true)"
  if [ -z "${content}" ]; then
    log "Release notes service returned no usable content."
    log "Response: $(cat response.json 2>/dev/null)"
    return 1
  fi

  printf '%s\n' "${content}"
}

if notes_body="$(generate_notes_body)"; then
  printf '%s\n' "${notes_body}" >notes.md
else
  annotate_skipped "Release notes could not be generated. Write them into the draft release by hand before publishing."
  : >notes.md
fi

# Add links to changelog and closed issues
echo "
[Full Changelog](https://github.com/${GH_REPO}/compare/${last_release}...${release_name}) \
· [Closed Issues](https://github.com/${GH_REPO}/milestone/${milestone}?closed=1)" >>notes.md

# Output the file path of the generated notes
readlink -f notes.md
