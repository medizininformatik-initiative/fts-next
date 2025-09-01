#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_NAME="fhir-packager"

# Default JVM options
JVM_OPTS="${JVM_OPTS:--Xms256m -Xmx2g -Dfile.encoding=UTF-8}"
# Convert JVM_OPTS to array for proper handling
read -ra JVM_OPTS_ARRAY <<< "$JVM_OPTS"

# Find the JAR file using find for better handling
JAR_FILE=$(find "$PROJECT_DIR/target" -name "${JAR_NAME}-*.jar" -type f | head -n 1)

if [[ -z "$JAR_FILE" ]]; then
    echo "Error: FHIR Packager JAR not found in target directory" >&2
    echo "Expected: $PROJECT_DIR/target/${JAR_NAME}-*.jar" >&2
    echo "" >&2
    echo "Build the JAR with:" >&2
    echo "  mvn clean package" >&2
    echo "  or" >&2
    echo "  make fhir-packager" >&2
    exit 1
fi

# Show help if requested
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    echo "FHIR Packager - Pseudonymize FHIR Bundles via CLI"
    echo ""
    echo "Usage:"
    echo "  $0 [OPTIONS]"
    echo ""
    echo "Examples:"
    echo "  # Basic usage (reads from stdin, writes to stdout)"
    echo "  cat bundle.json | $0 > pseudonymized-bundle.json"
    echo ""
    echo "  # With custom pseudonymizer service URL"
    echo "  cat bundle.json | $0 --pseudonymizer-url http://localhost:8080 > output.json"
    echo ""
    echo "  # With config file and verbose logging"
    echo "  $0 --config-file config.yaml --verbose < input.json > output.json"
    echo ""
    echo "  # Show application help"
    echo "  $0 --help"
    echo ""
    echo "Environment Variables:"
    echo "  JVM_OPTS          - JVM options (default: $JVM_OPTS)"
    echo "  PSEUDONYMIZER_URL - Pseudonymizer service URL"
    echo ""
    echo "Configuration:"
    echo "  The tool supports configuration via:"
    echo "  1. CLI arguments (highest priority)"
    echo "  2. Environment variables"
    echo "  3. application.yaml file (lowest priority)"
    echo ""
    echo "For detailed options, run without arguments to see application help."
    exit 0
fi

# Run the FHIR Packager
exec java "${JVM_OPTS_ARRAY[@]}" -jar "$JAR_FILE" "$@"