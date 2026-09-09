#!/usr/bin/env bash
# Run PIT mutation testing only on the production classes that changed on this
# branch compared with a base ref (default: origin/main).
#
# Usage: .github/scripts/mutation-pr.sh [BASE_REF] [extra mvn args...]
set -euo pipefail

base="${1:-origin/main}"
shift || true

mapfile -t files < <(git diff --name-only --diff-filter=ACMR "${base}...HEAD" -- '*/src/main/java/*.java')

if [[ ${#files[@]} -eq 0 ]]; then
  echo "No production Java files changed against ${base}; nothing to mutate."
  exit 0
fi

declare -A modules
classes=()
for f in "${files[@]}"; do
  module="${f%%/src/main/java/*}"
  [[ "${module}" == "test-util" ]] && continue
  modules["${module}"]=1
  cls="${f#*/src/main/java/}"
  cls="${cls%.java}"
  classes+=("${cls//\//.}*")
done

if [[ ${#classes[@]} -eq 0 ]]; then
  echo "Only test-util changed; nothing to mutate."
  exit 0
fi

target_classes=$(IFS=,; echo "${classes[*]}")
projects=$(IFS=,; echo "${!modules[*]}")

echo "Modules:  ${projects}"
echo "Classes:  ${target_classes}"

[[ -n "${PIT_DRY_RUN:-}" ]] && exit 0

mvn -B -Pmutation --also-make --projects "${projects}" \
  -DskipUnitTests=true -DskipITs -Dcheckstyle.skip -Djacoco.skip -Dcyclonedx.skip -Dspring-boot.repackage.skip=true \
  -DtargetClasses="${target_classes}" \
  "$@" verify
