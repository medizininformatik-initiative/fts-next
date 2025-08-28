#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "Starting FHIR Pseudonymizer..."
cd "$PROJECT_DIR"

# Start the service
docker-compose up -d fhir-pseudonymizer

# Get the assigned port
CONTAINER_NAME="fhir-packager-fhir-pseudonymizer-1"
echo "Getting assigned port..."
PORT=$(docker port "$CONTAINER_NAME" 8080 | cut -d: -f2)

if [ -z "$PORT" ]; then
    echo "Failed to get assigned port"
    echo "Check container status with: docker-compose ps"
    exit 1
fi

echo "FHIR Pseudonymizer assigned to port: $PORT"
echo "Waiting for FHIR Pseudonymizer to be ready..."

# Wait for health check to pass with timeout
timeout 60 bash -c "
    while true; do
        if curl -sf http://localhost:$PORT/fhir/metadata > /dev/null 2>&1; then
            echo 'FHIR Pseudonymizer is ready!'
            break
        fi
        echo -n '.'
        sleep 2
    done
"

if [ $? -ne 0 ]; then
    echo "Timeout waiting for FHIR Pseudonymizer to become ready"
    echo "Check logs with: docker-compose logs fhir-pseudonymizer"
    exit 1
fi

echo "FHIR Pseudonymizer is ready at http://localhost:$PORT"
echo "API endpoint: http://localhost:$PORT/fhir/\$de-identify"
echo "Stop with: docker-compose down"