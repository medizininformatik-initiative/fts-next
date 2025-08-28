#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

echo "Testing FHIR Pseudonymizer..."

# Get the port from the running container
CONTAINER_NAME="fhir-packager-fhir-pseudonymizer-1"
PORT=$(docker port "$CONTAINER_NAME" 8080 2>/dev/null | cut -d: -f2)

if [ -z "$PORT" ]; then
    echo "FHIR Pseudonymizer container not running"
    echo "Start it with: ./scripts/start-pseudonymizer.sh"
    exit 1
fi

echo "Found FHIR Pseudonymizer on port: $PORT"

# Check if service is healthy using FHIR metadata endpoint
if ! curl -sf http://localhost:$PORT/fhir/metadata > /dev/null; then
    echo "FHIR Pseudonymizer is not healthy"
    echo "Check logs with: docker-compose logs fhir-pseudonymizer"
    exit 1
fi

echo "✓ FHIR Pseudonymizer is healthy"

# Test with a simple bundle if available
TEST_FILE="$PROJECT_DIR/src/test/resources/fixtures/small-bundle.json"

if [ -f "$TEST_FILE" ]; then
    echo "Testing pseudonymization with test bundle..."
    
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type:application/fhir+json" \
        "http://localhost:$PORT/fhir/\$de-identify" \
        -d @"$TEST_FILE")
    
    if echo "$RESPONSE" | jq . > /dev/null 2>&1; then
        echo "✓ Successfully pseudonymized test bundle"
        echo "Response preview:"
        echo "$RESPONSE" | jq -C '.resourceType, .entry | length'
    else
        echo "✗ Invalid JSON response from pseudonymizer"
        echo "Response: $RESPONSE"
        exit 1
    fi
else
    echo "⚠ Test bundle not found at $TEST_FILE"
    echo "Testing with minimal Patient resource..."
    
    MINIMAL_BUNDLE='{
        "resourceType": "Bundle",
        "id": "test-bundle",
        "type": "collection",
        "entry": [{
            "resource": {
                "resourceType": "Patient",
                "id": "test-patient",
                "name": [{
                    "family": "Doe",
                    "given": ["John"]
                }],
                "birthDate": "1990-01-01"
            }
        }]
    }'
    
    RESPONSE=$(curl -s -X POST \
        -H "Content-Type:application/fhir+json" \
        "http://localhost:$PORT/fhir/\$de-identify" \
        -d "$MINIMAL_BUNDLE")
    
    if echo "$RESPONSE" | jq . > /dev/null 2>&1; then
        echo "✓ Successfully pseudonymized minimal bundle"
        echo "Response preview:"
        echo "$RESPONSE" | jq -C '.resourceType, .entry | length'
    else
        echo "✗ Invalid JSON response from pseudonymizer"
        echo "Response: $RESPONSE"
        exit 1
    fi
fi

echo "✓ FHIR Pseudonymizer test completed successfully"