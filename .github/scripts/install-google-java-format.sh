#!/bin/bash
set -euo pipefail

VERSION="${JAVA_FORMAT_VERSION#v}"
CHECKSUM="${JAVA_FORMAT_CHECKSUM}"

url="https://github.com/google/google-java-format/releases/download/v${VERSION}/google-java-format-${VERSION}-all-deps.jar"
curl -sSfL "${url}" -o google-java-format.jar
sha256sum -c <(echo "${CHECKSUM} google-java-format.jar")

mv google-java-format.jar /tmp/google-java-format.jar
