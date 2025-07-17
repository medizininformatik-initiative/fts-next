#!/bin/bash
set -euo pipefail

# Get release_name version from command line argument
release_name="${1}"

# Extract milestone name
# * remove leading v
# * remove prerelease identifiers
# * remove bugfix release identifier when '.0' release
milestone_name="$(echo "${release_name#v}" | cut -d- -f1 | sed 's/\.0$//')"

# Get milestone from GH API
gh extension install valeriobelli/gh-milestone &>/dev/null
milestone="$(gh milestone ls --query "${milestone_name}" --json number | jq -e '.[].number')"

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

# Send prompt file to Mistral API and capture the response
jq -n --rawfile content prompt.md \
  '{"model": "mistral-medium", "messages": [{"role": "user", "content": $content}]}' \
| curl -sf https://api.mistral.ai/v1/chat/completions -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${MISTRAL_API_KEY}" --data-binary @- \
| jq -r '.choices[0].message.content' >notes.md

# Add links to changelog and closed issues
last_release="$(gh release ls --limit 1 --exclude-drafts --json tagName | jq -er '.[].tagName')"
echo "
[Full Changelog](https://github.com/${GH_REPO}/compare/${last_release}...${release_name}) \
· [Closed Issues](https://github.com/${GH_REPO}/milestone/${milestone}?closed=1)" >>notes.md

# Output the file path of the generated notes
readlink -f notes.md
