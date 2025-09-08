# Usage Examples

This guide provides practical examples of using the FHIR Packager in various scenarios, from simple command-line operations to complex data processing workflows.

## Basic Usage Patterns

### Simple Pseudonymization

The most basic usage pattern for pseudonymizing a single FHIR Bundle:

```bash
# Read from file, write to file
cat patient-bundle.json | java -jar fhir-packager.jar > pseudonymized-bundle.json

# Check exit code for success
if [ $? -eq 0 ]; then
  echo "Pseudonymization successful"
else
  echo "Pseudonymization failed"
fi
```

### With Custom Configuration

```bash
# Specify service URL and timeout
cat bundle.json | java -jar fhir-packager.jar \
  --pseudonymizer-url https://pseudonymizer.example.com \
  --timeout 60 > result.json

# Enable verbose logging
cat bundle.json | java -jar fhir-packager.jar \
  --verbose > result.json 2>debug.log
```

### Using Configuration Files

```bash
# Create configuration file
cat > config.yaml << 'EOF'
pseudonymizer:
  url: https://pseudonymizer.prod.example.com
  connect-timeout: 45s
  read-timeout: 120s
  retry:
    max-attempts: 5
EOF

# Use configuration file
cat bundle.json | java -jar fhir-packager.jar \
  --config-file config.yaml > result.json
```

## Data Pipeline Integration

### FHIR Server to Research System

Extract data from a clinical FHIR server, pseudonymize it, and load it into a research system:

```bash
#!/bin/bash
set -euo pipefail

CLINICAL_SERVER="https://clinical-fhir.example.com"
RESEARCH_SERVER="https://research-fhir.example.com"
PSEUDONYMIZER_URL="https://pseudonymizer.example.com"

# Process single patient
process_patient() {
  local patient_id=$1
  
  echo "Processing patient: $patient_id"
  
  # Extract patient data using $everything
  curl -s -H "Accept: application/fhir+json" \
    "$CLINICAL_SERVER/Patient/$patient_id/\$everything" | \
  
  # Pseudonymize the bundle
  java -jar fhir-packager.jar \
    --pseudonymizer-url "$PSEUDONYMIZER_URL" \
    --timeout 120 | \
  
  # Upload to research system
  curl -X POST \
    -H "Content-Type: application/fhir+json" \
    -d @- \
    "$RESEARCH_SERVER/Bundle"
  
  echo "Completed patient: $patient_id"
}

# Process list of patients
while IFS= read -r patient_id; do
  process_patient "$patient_id"
done < patient-list.txt
```

### Batch File Processing

Process multiple FHIR Bundle files in a directory:

```bash
#!/bin/bash
set -euo pipefail

INPUT_DIR="./bundles"
OUTPUT_DIR="./pseudonymized"
ERROR_LOG="processing-errors.log"

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Clear previous error log
> "$ERROR_LOG"

# Process all JSON files
for bundle_file in "$INPUT_DIR"/*.json; do
  if [ -f "$bundle_file" ]; then
    filename=$(basename "$bundle_file")
    output_file="$OUTPUT_DIR/$filename"
    
    echo "Processing: $filename"
    
    if cat "$bundle_file" | \
       java -jar fhir-packager.jar \
         --pseudonymizer-url https://pseudonymizer.example.com \
         --timeout 60 > "$output_file" 2>>"$ERROR_LOG"; then
      echo "✓ Success: $filename"
    else
      echo "✗ Failed: $filename (exit code: $?)"
      rm -f "$output_file"  # Clean up partial file
    fi
  fi
done

echo "Processing complete. Check $ERROR_LOG for errors."
```

### Parallel Processing

Process multiple bundles in parallel for improved throughput:

```bash
#!/bin/bash
set -euo pipefail

INPUT_DIR="./bundles"
OUTPUT_DIR="./pseudonymized"
MAX_PARALLEL=5  # Number of parallel processes

mkdir -p "$OUTPUT_DIR"

# Function to process a single bundle
process_bundle() {
  local input_file=$1
  local filename=$(basename "$input_file")
  local output_file="$OUTPUT_DIR/$filename"
  
  echo "Starting: $filename (PID: $$)"
  
  if cat "$input_file" | \
     java -jar fhir-packager.jar \
       --pseudonymizer-url https://pseudonymizer.example.com \
       --timeout 60 > "$output_file"; then
    echo "✓ Completed: $filename"
  else
    echo "✗ Failed: $filename"
    rm -f "$output_file"
    return 1
  fi
}

# Export function for parallel execution
export -f process_bundle

# Process files in parallel
find "$INPUT_DIR" -name "*.json" -print0 | \
  xargs -0 -P "$MAX_PARALLEL" -I {} bash -c 'process_bundle "$@"' _ {}

echo "All processing jobs submitted"
```

## Integration with Workflow Systems

### Apache Airflow DAG

```python
from datetime import datetime, timedelta
from airflow import DAG
from airflow.operators.bash import BashOperator
from airflow.operators.python import PythonOperator

default_args = {
    'owner': 'data-team',
    'depends_on_past': False,
    'start_date': datetime(2024, 1, 1),
    'email_on_failure': True,
    'email_on_retry': False,
    'retries': 2,
    'retry_delay': timedelta(minutes=5)
}

dag = DAG(
    'fhir_pseudonymization_pipeline',
    default_args=default_args,
    description='Daily FHIR data pseudonymization',
    schedule_interval='@daily',
    catchup=False
)

# Extract FHIR data
extract_task = BashOperator(
    task_id='extract_fhir_data',
    bash_command='''
    curl -s "{{ params.clinical_server }}/Bundle?_lastUpdated=ge{{ ds }}" \
      > /tmp/daily-bundle-{{ ds }}.json
    ''',
    params={'clinical_server': 'https://clinical-fhir.example.com'},
    dag=dag
)

# Pseudonymize data
pseudonymize_task = BashOperator(
    task_id='pseudonymize_data',
    bash_command='''
    cat /tmp/daily-bundle-{{ ds }}.json | \
    java -jar /opt/fhir-packager/fhir-packager.jar \
      --pseudonymizer-url {{ params.pseudonymizer_url }} \
      --timeout 300 > /tmp/pseudonymized-bundle-{{ ds }}.json
    ''',
    params={'pseudonymizer_url': 'https://pseudonymizer.example.com'},
    dag=dag
)

# Load to research system
load_task = BashOperator(
    task_id='load_to_research',
    bash_command='''
    curl -X POST \
      -H "Content-Type: application/fhir+json" \
      --data-binary @/tmp/pseudonymized-bundle-{{ ds }}.json \
      "{{ params.research_server }}/Bundle"
    ''',
    params={'research_server': 'https://research-fhir.example.com'},
    dag=dag
)

# Cleanup temporary files
cleanup_task = BashOperator(
    task_id='cleanup',
    bash_command='rm -f /tmp/*bundle-{{ ds }}.json',
    dag=dag
)

# Define task dependencies
extract_task >> pseudonymize_task >> load_task >> cleanup_task
```

### GitHub Actions Workflow

```yaml
name: FHIR Data Processing

on:
  schedule:
    - cron: '0 2 * * *'  # Daily at 2 AM
  workflow_dispatch:

jobs:
  process-fhir-data:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      
    - name: Setup Java
      uses: actions/setup-java@v4
      with:
        distribution: 'temurin'
        java-version: '21'
        
    - name: Build FHIR Packager
      run: |
        cd fhir-packager
        mvn clean package -DskipTests
        
    - name: Download FHIR data
      run: |
        curl -s "${{ secrets.CLINICAL_FHIR_SERVER }}/Bundle?_lastUpdated=ge$(date -d '1 day ago' +%Y-%m-%d)" \
          > input-bundle.json
          
    - name: Pseudonymize FHIR data
      run: |
        cat input-bundle.json | \
        java -jar fhir-packager/target/fhir-packager-*.jar \
          --pseudonymizer-url "${{ secrets.PSEUDONYMIZER_URL }}" \
          --timeout 300 > pseudonymized-bundle.json
          
    - name: Upload to research system
      run: |
        curl -X POST \
          -H "Content-Type: application/fhir+json" \
          -H "Authorization: Bearer ${{ secrets.RESEARCH_API_TOKEN }}" \
          --data-binary @pseudonymized-bundle.json \
          "${{ secrets.RESEARCH_FHIR_SERVER }}/Bundle"
          
    - name: Cleanup
      if: always()
      run: |
        rm -f input-bundle.json pseudonymized-bundle.json
```

## Error Handling and Monitoring

### Robust Error Handling

```bash
#!/bin/bash
set -euo pipefail

LOGFILE="/var/log/fhir-processing.log"
METRIC_FILE="/tmp/processing-metrics.json"

# Logging function
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOGFILE"
}

# Process bundle with comprehensive error handling
process_with_retry() {
  local input_file=$1
  local output_file=$2
  local max_attempts=3
  local attempt=1
  
  while [ $attempt -le $max_attempts ]; do
    log "Attempt $attempt/$max_attempts for $(basename "$input_file")"
    
    # Start timing
    start_time=$(date +%s)
    
    if cat "$input_file" | \
       timeout 300 java -jar fhir-packager.jar \
         --pseudonymizer-url https://pseudonymizer.example.com \
         --timeout 240 > "$output_file" 2>"$output_file.error"; then
      
      # Success - log metrics
      end_time=$(date +%s)
      duration=$((end_time - start_time))
      
      log "✓ Success: $(basename "$input_file") (${duration}s)"
      
      # Record metrics
      jq -n \
        --arg file "$(basename "$input_file")" \
        --arg duration "$duration" \
        --arg timestamp "$(date -Iseconds)" \
        --arg status "success" \
        '{file: $file, duration: ($duration|tonumber), timestamp: $timestamp, status: $status}' \
        >> "$METRIC_FILE"
      
      rm -f "$output_file.error"
      return 0
    else
      exit_code=$?
      end_time=$(date +%s)
      duration=$((end_time - start_time))
      
      case $exit_code in
        1)
          log "✗ Service error for $(basename "$input_file") (attempt $attempt)"
          ;;
        2)
          log "✗ Invalid arguments for $(basename "$input_file")"
          return $exit_code  # Don't retry for argument errors
          ;;
        3)
          log "✗ Invalid FHIR Bundle: $(basename "$input_file")"
          return $exit_code  # Don't retry for invalid bundles
          ;;
        124)
          log "✗ Timeout processing $(basename "$input_file")"
          ;;
        *)
          log "✗ Unknown error ($exit_code) for $(basename "$input_file")"
          ;;
      esac
      
      # Log error details
      if [ -f "$output_file.error" ]; then
        log "Error details: $(cat "$output_file.error")"
      fi
      
      # Record failed attempt
      jq -n \
        --arg file "$(basename "$input_file")" \
        --arg duration "$duration" \
        --arg timestamp "$(date -Iseconds)" \
        --arg status "failed" \
        --arg exit_code "$exit_code" \
        --arg attempt "$attempt" \
        '{file: $file, duration: ($duration|tonumber), timestamp: $timestamp, status: $status, exit_code: ($exit_code|tonumber), attempt: ($attempt|tonumber)}' \
        >> "$METRIC_FILE"
      
      rm -f "$output_file" "$output_file.error"
      
      if [ $attempt -eq $max_attempts ]; then
        log "✗ Failed after $max_attempts attempts: $(basename "$input_file")"
        return $exit_code
      fi
      
      # Wait before retry (exponential backoff)
      sleep_time=$((attempt * attempt * 10))
      log "Waiting ${sleep_time}s before retry..."
      sleep $sleep_time
    fi
    
    ((attempt++))
  done
}

# Example usage
process_with_retry "patient-bundle.json" "pseudonymized-bundle.json"
```

### Health Monitoring

```bash
#!/bin/bash
# health-check.sh - Monitor FHIR Packager and service health

PSEUDONYMIZER_URL="https://pseudonymizer.example.com"
TEST_BUNDLE='{"resourceType":"Bundle","type":"collection","entry":[]}'
HEALTH_LOG="/var/log/fhir-packager-health.log"

# Function to log with timestamp
log_health() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$HEALTH_LOG"
}

# Test service availability
test_service_health() {
  if curl -f -s "$PSEUDONYMIZER_URL/health" > /dev/null; then
    log_health "✓ Service health check passed"
    return 0
  else
    log_health "✗ Service health check failed"
    return 1
  fi
}

# Test end-to-end functionality
test_e2e() {
  if echo "$TEST_BUNDLE" | \
     timeout 30 java -jar fhir-packager.jar \
       --pseudonymizer-url "$PSEUDONYMIZER_URL" \
       --timeout 20 > /dev/null 2>&1; then
    log_health "✓ End-to-end test passed"
    return 0
  else
    log_health "✗ End-to-end test failed (exit code: $?)"
    return 1
  fi
}

# Run health checks
main() {
  log_health "Starting health check"
  
  local service_ok=false
  local e2e_ok=false
  
  if test_service_health; then
    service_ok=true
  fi
  
  if test_e2e; then
    e2e_ok=true
  fi
  
  if $service_ok && $e2e_ok; then
    log_health "✓ All health checks passed"
    exit 0
  else
    log_health "✗ Health check failures detected"
    exit 1
  fi
}

main "$@"
```

## Performance Optimization

### Large Bundle Processing

```bash
#!/bin/bash
# Optimized for processing large bundles

# Increase JVM memory for large bundles
export JAVA_OPTS="-Xmx8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Process large bundle with extended timeout
process_large_bundle() {
  local input_file=$1
  local output_file=$2
  
  echo "Processing large bundle: $(basename "$input_file")"
  echo "File size: $(du -h "$input_file" | cut -f1)"
  
  # Monitor processing
  {
    cat "$input_file" | \
    java $JAVA_OPTS -jar fhir-packager.jar \
      --pseudonymizer-url https://pseudonymizer.example.com \
      --timeout 1800 \
      --retries 2 > "$output_file"
  } &
  
  local pid=$!
  
  # Monitor progress
  while kill -0 $pid 2>/dev/null; do
    echo "Processing... ($(date))"
    sleep 30
  done
  
  wait $pid
  local exit_code=$?
  
  if [ $exit_code -eq 0 ]; then
    echo "✓ Large bundle processed successfully"
    echo "Output size: $(du -h "$output_file" | cut -f1)"
  else
    echo "✗ Large bundle processing failed (exit code: $exit_code)"
    rm -f "$output_file"
  fi
  
  return $exit_code
}

# Example usage
process_large_bundle "huge-patient-bundle.json" "huge-pseudonymized-bundle.json"
```

### Memory-Efficient Streaming

For very large bundles that might exceed memory limits:

```bash
#!/bin/bash
# Stream processing for memory efficiency

# Function to split large bundle into smaller chunks
split_bundle() {
  local input_file=$1
  local chunk_size=${2:-1000}  # Resources per chunk
  local output_prefix=$3
  
  echo "Splitting bundle into chunks of $chunk_size resources each"
  
  # Use jq to split bundle (requires jq installed)
  jq -c --argjson size $chunk_size '
    .entry as $entries |
    ($entries | length) as $total |
    range(0; $total; $size) as $i |
    {
      resourceType: .resourceType,
      type: .type,
      entry: $entries[$i:$i+$size]
    }
  ' "$input_file" | \
  
  # Write each chunk to separate file
  {
    chunk_num=1
    while IFS= read -r chunk; do
      echo "$chunk" > "${output_prefix}_chunk_${chunk_num}.json"
      echo "Created chunk $chunk_num"
      ((chunk_num++))
    done
  }
}

# Process chunks in parallel
process_chunks_parallel() {
  local chunk_prefix=$1
  local output_prefix=$2
  local max_parallel=${3:-5}
  
  # Function to process a single chunk
  process_chunk() {
    local chunk_file=$1
    local chunk_num=$(echo "$chunk_file" | sed 's/.*chunk_\([0-9]*\)\.json/\1/')
    local output_file="${output_prefix}_pseudonymized_${chunk_num}.json"
    
    echo "Processing chunk $chunk_num"
    
    if cat "$chunk_file" | \
       java -jar fhir-packager.jar \
         --pseudonymizer-url https://pseudonymizer.example.com \
         --timeout 300 > "$output_file"; then
      echo "✓ Chunk $chunk_num completed"
      rm -f "$chunk_file"  # Clean up input chunk
    else
      echo "✗ Chunk $chunk_num failed"
      rm -f "$output_file"
      return 1
    fi
  }
  
  export -f process_chunk
  
  # Process all chunks in parallel
  find . -name "${chunk_prefix}_chunk_*.json" -print0 | \
    xargs -0 -P "$max_parallel" -I {} bash -c 'process_chunk "$@"' _ {}
}

# Merge processed chunks back into single bundle
merge_chunks() {
  local output_prefix=$1
  local final_output=$2
  
  echo "Merging processed chunks into final bundle"
  
  # Use jq to merge all chunks
  jq -s '
    {
      resourceType: "Bundle",
      type: .[0].type,
      entry: [.[].entry[]]
    }
  ' "${output_prefix}_pseudonymized_"*.json > "$final_output"
  
  # Clean up intermediate files
  rm -f "${output_prefix}_pseudonymized_"*.json
  
  echo "Final bundle created: $final_output"
}

# Complete workflow for large bundle
process_large_bundle_streaming() {
  local input_file=$1
  local output_file=$2
  local chunk_size=${3:-1000}
  local max_parallel=${4:-5}
  
  local base_name=$(basename "$input_file" .json)
  
  echo "Starting streaming processing of $input_file"
  
  # Step 1: Split into chunks
  split_bundle "$input_file" "$chunk_size" "$base_name"
  
  # Step 2: Process chunks in parallel
  process_chunks_parallel "$base_name" "$base_name" "$max_parallel"
  
  # Step 3: Merge results
  merge_chunks "$base_name" "$output_file"
  
  echo "Streaming processing complete: $output_file"
}

# Example usage
process_large_bundle_streaming "massive-bundle.json" "massive-pseudonymized.json" 500 3
```

## Testing and Validation

### Automated Testing Pipeline

```bash
#!/bin/bash
# test-pipeline.sh - Comprehensive testing of FHIR processing

set -euo pipefail

TEST_DIR="./test-data"
RESULTS_DIR="./test-results"
PSEUDONYMIZER_URL="https://pseudonymizer-test.example.com"

# Clean up and prepare
cleanup() {
  rm -rf "$RESULTS_DIR"
  mkdir -p "$RESULTS_DIR"
}

# Generate test data
generate_test_data() {
  echo "Generating test data..."
  
  # Small test bundle
  cat > "$TEST_DIR/small-bundle.json" << 'EOF'
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "resource": {
        "resourceType": "Patient",
        "id": "test-patient-1",
        "name": [{"family": "TestFamily", "given": ["TestGiven"]}]
      }
    }
  ]
}
EOF

  # Invalid bundle for error testing
  cat > "$TEST_DIR/invalid-bundle.json" << 'EOF'
{
  "resourceType": "InvalidResource",
  "invalid": true
}
EOF

  # Empty bundle
  cat > "$TEST_DIR/empty-bundle.json" << 'EOF'
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": []
}
EOF
}

# Test successful processing
test_success_cases() {
  echo "Testing successful processing cases..."
  
  local test_files=("small-bundle.json" "empty-bundle.json")
  
  for test_file in "${test_files[@]}"; do
    echo "Testing: $test_file"
    
    if cat "$TEST_DIR/$test_file" | \
       java -jar fhir-packager.jar \
         --pseudonymizer-url "$PSEUDONYMIZER_URL" \
         --timeout 30 > "$RESULTS_DIR/result-$test_file"; then
      echo "✓ Success: $test_file"
    else
      echo "✗ Failed: $test_file"
      exit 1
    fi
  done
}

# Test error handling
test_error_cases() {
  echo "Testing error handling..."
  
  # Test invalid bundle (should exit with code 3)
  if cat "$TEST_DIR/invalid-bundle.json" | \
     java -jar fhir-packager.jar \
       --pseudonymizer-url "$PSEUDONYMIZER_URL" \
       --timeout 30 > /dev/null 2>&1; then
    echo "✗ Invalid bundle should have failed"
    exit 1
  else
    exit_code=$?
    if [ $exit_code -eq 3 ]; then
      echo "✓ Invalid bundle correctly rejected (exit code 3)"
    else
      echo "✗ Invalid bundle failed with wrong exit code: $exit_code"
      exit 1
    fi
  fi
  
  # Test invalid URL (should exit with code 2)
  if echo '{"resourceType":"Bundle","type":"collection","entry":[]}' | \
     java -jar fhir-packager.jar \
       --pseudonymizer-url "invalid-url" > /dev/null 2>&1; then
    echo "✗ Invalid URL should have failed"
    exit 1
  else
    exit_code=$?
    if [ $exit_code -eq 2 ]; then
      echo "✓ Invalid URL correctly rejected (exit code 2)"
    else
      echo "✗ Invalid URL failed with wrong exit code: $exit_code"
      exit 1
    fi
  fi
}

# Performance testing
test_performance() {
  echo "Testing performance..."
  
  local test_file="$TEST_DIR/small-bundle.json"
  local iterations=10
  local total_time=0
  
  for i in $(seq 1 $iterations); do
    start_time=$(date +%s%N)
    
    cat "$test_file" | \
    java -jar fhir-packager.jar \
      --pseudonymizer-url "$PSEUDONYMIZER_URL" \
      --timeout 30 > /dev/null
    
    end_time=$(date +%s%N)
    duration=$(( (end_time - start_time) / 1000000 ))  # Convert to milliseconds
    total_time=$((total_time + duration))
    
    echo "Iteration $i: ${duration}ms"
  done
  
  average_time=$((total_time / iterations))
  echo "Average processing time: ${average_time}ms"
  
  # Check if performance is acceptable (< 5 seconds)
  if [ $average_time -lt 5000 ]; then
    echo "✓ Performance test passed"
  else
    echo "✗ Performance test failed: average time ${average_time}ms > 5000ms"
    exit 1
  fi
}

# Main test execution
main() {
  echo "Starting FHIR Packager test suite"
  
  cleanup
  generate_test_data
  test_success_cases
  test_error_cases
  test_performance
  
  echo "✓ All tests passed successfully"
}

main "$@"
```

This comprehensive usage guide provides practical examples for integrating the FHIR Packager into various workflows, from simple command-line usage to complex automated data processing pipelines. The examples demonstrate proper error handling, performance optimization, and monitoring practices for production use.