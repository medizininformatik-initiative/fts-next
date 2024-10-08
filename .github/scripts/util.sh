#!/bin/bash
set -euo pipefail

assert() {
  if [ "$2" = "$3" ]; then
    echo -e "  OK ✅  the $1 is $3"
  else
    echo -e "Fail ❌  the $1 is $2, expected $3"
    exit 1
  fi
}

assert-ge() {
  if [ "$2" -ge "$3" ]; then
    echo -e "  OK ✅  the $1 of $2 is >= $3"
  else
    echo -e "Fail ❌  the $1 is $2, expected >= $3"
    exit 1
  fi
}
