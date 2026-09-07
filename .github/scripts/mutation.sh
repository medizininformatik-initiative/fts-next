#!/usr/bin/env bash
# Run PIT mutation testing over all modules.
#
# Reports land in <module>/target/pit-reports. List the survivors with
# .github/scripts/mutation-survived.sh.
#
# Usage: .github/scripts/mutation.sh [extra mvn args...]
# To run one module: MAVEN_ARGS="-pl util" .github/scripts/mutation.sh
set -euo pipefail

# shellcheck disable=SC2086
mvn ${MAVEN_ARGS:-} -B -Pmutation \
  -DskipUnitTests=true -DskipITs -Dcheckstyle.skip -Djacoco.skip -Dcyclonedx.skip \
  -Dspring-boot.repackage.skip=true \
  "$@" verify
