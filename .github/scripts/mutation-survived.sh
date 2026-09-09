#!/usr/bin/env bash
# Print every mutant that survived, from all <module>/target/pit-reports/mutations.xml files.
#
# PIT has no JSON report format, so xq reads mutations.xml and applies a jq program
# to it. xq comes from the python yq package: `pipx install yq`. It writes XML
# attributes with an "@" prefix, and it needs the jq binary on the PATH.
#
# Usage: .github/scripts/mutation-survived.sh [--json] [module...]
set -euo pipefail

command -v xq >/dev/null || { echo "Needs xq: pipx install yq" >&2; exit 1; }

json=false
if [[ "${1:-}" == "--json" ]]; then
  json=true
  shift
fi

modules=("$@")
[[ ${#modules[@]} -eq 0 ]] && modules=(api util clinical-domain-agent trust-center-agent research-domain-agent)

# Keep the survivors, and add the path of the source file.
program='
  .mutations.mutation
| (if . == null then [] elif type == "array" then . else [.] end)
| map(select(."@status" == "SURVIVED" or ."@status" == "NO_COVERAGE"))
| .[]
| {
    status: ."@status",
    sourceFile: .sourceFile,
    class: .mutatedClass,
    method: .mutatedMethod,
    line: (.lineNumber | tonumber),
    mutator: (.mutator | split(".") | last),
    description: .description,
    module: $module,
    file: ($module + "/src/main/java/" + (.mutatedClass | split("$") | .[0] | gsub("\\."; "/")) + ".java")
  }
'

total=0
for m in "${modules[@]}"; do
  f="${m}/target/pit-reports/mutations.xml"
  [[ -s "${f}" ]] || continue

  if "${json}"; then
    out=$(xq -c --arg module "${m}" "${program}" "${f}")
  else
    out=$(xq -r --arg module "${m}" \
      "${program} | \"\(.file):\(.line)  \(.status)  \(.method)  \(.description)\"" "${f}")
  fi

  count=0
  if [[ -n "${out}" ]]; then
    printf '%s\n' "${out}"
    count=$(printf '%s\n' "${out}" | wc -l)
  fi

  total=$((total + count))
  echo "# ${m}: ${count} surviving" >&2
done

echo "# total: ${total} surviving" >&2
