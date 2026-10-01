#!/usr/bin/env bash
# Print, one per line, every module whose sources, tests or pom.xml changed on
# this branch compared with a base ref (default: origin/main).
#
# The mutation workflow mutates each printed module in full, so the
# mutationThreshold of its pom.xml applies. Deleted files count, because a
# deleted test can lower the score. Changes to test-util or the root pom.xml
# print nothing: they touch every module, and the weekly full run catches the
# drift they cause.
#
# Usage: .github/scripts/mutation-modules.sh [BASE_REF]
set -euo pipefail

base="${1:-origin/main}"

git diff --name-only "${base}...HEAD" -- '*/src/main/*' '*/src/test/*' '*/pom.xml' \
  | sed -E 's#/(src/(main|test)/.*|pom\.xml)$##' \
  | { grep -vx 'test-util' || true; } \
  | sort -u
