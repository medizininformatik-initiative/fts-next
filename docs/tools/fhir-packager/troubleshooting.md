# Troubleshooting Guide

This guide helps you diagnose and resolve common issues when using the FHIR Packager.

## Exit Codes

The FHIR Packager uses specific exit codes to indicate different types of errors:

| Exit Code | Meaning | Common Causes | Resolution |
|-----------|---------|---------------|------------|
| `0` | Success | - | Normal completion |
| `1` | General error | Network issues, service errors, timeouts | Check connectivity, service status, logs |
| `2` | Invalid arguments | Wrong command-line arguments, invalid URLs | Check command syntax, fix arguments |
| `3` | Invalid FHIR Bundle | Malformed JSON, invalid FHIR structure | Validate input bundle format |

### Exit Code Examples

```bash
# Check exit code in scripts
cat bundle.json | java -jar fhir-packager.jar
case $? in
  0) echo "Success" ;;
  1) echo "Service or network error" ;;
  2) echo "Invalid command line arguments" ;;
  3) echo "Invalid FHIR Bundle format" ;;
  *) echo "Unexpected error" ;;
esac
```

## Common Error Scenarios

### 1. Service Connection Issues

#### Symptoms
```
Error: Connection refused
java.net.ConnectException: Connection refused
```

#### Causes
- FHIR Pseudonymizer service is not running
- Wrong service URL
- Network connectivity issues
- Firewall blocking connection

#### Solutions

**Check service status:**
```bash
# Test direct connection
curl -f http://localhost:8080/health
# or
telnet localhost 8080
```

**Verify configuration:**
```bash
# Check current configuration
java -jar fhir-packager.jar --help

# Test with explicit URL
java -jar fhir-packager.jar --pseudonymizer-url http://correct-host:8080
```

**Debug network connectivity:**
```bash
# Check if port is open
nmap -p 8080 pseudonymizer-host

# Check DNS resolution
nslookup pseudonymizer-host

# Test with different URL
ping pseudonymizer-host
```

### 2. Timeout Issues

#### Symptoms
```
Error: Read timeout
java.net.SocketTimeoutException: Read timeout
```

#### Causes
- Large bundle processing time exceeds timeout
- Slow network connection
- Service overload
- Default timeout too short

#### Solutions

**Increase timeout:**
```bash
# For large bundles
java -jar fhir-packager.jar --timeout 300  # 5 minutes

# For very large bundles
java -jar fhir-packager.jar --timeout 1800  # 30 minutes
```

**Configure via environment:**
```bash
export PSEUDONYMIZER_READ_TIMEOUT=600
java -jar fhir-packager.jar
```

**Configure via YAML:**
```yaml
pseudonymizer:
  read-timeout: 600s
  connect-timeout: 45s
```

**Monitor processing time:**
```bash
# Time the operation
time cat bundle.json | java -jar fhir-packager.jar --timeout 600
```

### 3. Invalid FHIR Bundle Format

#### Symptoms
```
Error: Invalid FHIR Bundle format
Exit code: 3
```

#### Causes
- Malformed JSON
- Missing required FHIR Bundle fields
- Invalid FHIR resource structure
- Encoding issues

#### Solutions

**Validate JSON format:**
```bash
# Check if valid JSON
cat bundle.json | jq . > /dev/null
echo "JSON validation exit code: $?"
```

**Validate FHIR Bundle structure:**
```bash
# Check required fields
cat bundle.json | jq '.resourceType, .type'

# Should output something like:
# "Bundle"
# "collection"
```

**Use FHIR validator:**
```bash
# Download and use official FHIR validator
java -jar validator_cli.jar bundle.json -version 4.0.1
```

**Check encoding:**
```bash
# Check file encoding
file bundle.json
# Should show: UTF-8 Unicode text

# Convert if needed
iconv -f ISO-8859-1 -t UTF-8 bundle.json > bundle-utf8.json
```

### 4. Memory Issues

#### Symptoms
```
java.lang.OutOfMemoryError: Java heap space
Error: Cannot allocate memory
```

#### Causes
- Very large FHIR bundles
- Insufficient JVM heap size
- Memory leaks in processing
- System memory pressure

#### Solutions

**Increase JVM memory:**
```bash
# Increase heap size
java -Xmx4g -jar fhir-packager.jar < large-bundle.json

# For very large bundles
java -Xmx8g -XX:+UseG1GC -jar fhir-packager.jar < huge-bundle.json
```

**Monitor memory usage:**
```bash
# Enable GC logging
java -XX:+PrintGCDetails -Xloggc:gc.log -jar fhir-packager.jar

# Monitor during processing
top -p $(pgrep java)
```

**Process in chunks:**
```bash
# Split large bundle into smaller parts
jq -c '.entry[]' large-bundle.json | split -l 1000 - chunk-

# Process each chunk separately
for chunk in chunk-*; do
  echo '{"resourceType":"Bundle","type":"collection","entry":[' > temp-bundle.json
  cat "$chunk" | sed 's/$/,/' | sed '$ s/,$//' >> temp-bundle.json
  echo ']}' >> temp-bundle.json
  
  java -jar fhir-packager.jar < temp-bundle.json > "result-$chunk.json"
done
```

### 5. Authentication/Authorization Issues

#### Symptoms
```
HTTP 401 Unauthorized
HTTP 403 Forbidden
```

#### Causes
- Missing authentication headers
- Invalid credentials
- Expired tokens
- Insufficient permissions

#### Solutions

::: info Future Enhancement
Authentication support is planned for future versions. Current workaround involves using a proxy or load balancer for authentication.
:::

**Use proxy for authentication:**
```bash
# Set up nginx proxy with auth
# nginx.conf:
# location /pseudonymizer/ {
#   proxy_pass http://backend-service/;
#   proxy_set_header Authorization "Bearer $token";
# }

java -jar fhir-packager.jar --pseudonymizer-url http://nginx-proxy/pseudonymizer/
```

**Use curl wrapper:**
```bash
#!/bin/bash
# auth-wrapper.sh
curl -H "Authorization: Bearer $API_TOKEN" \
  -H "Content-Type: application/fhir+json" \
  -X POST \
  --data-binary @- \
  "$PSEUDONYMIZER_URL/fhir/\$de-identify"
```

### 6. SSL/TLS Issues

#### Symptoms
```
SSLHandshakeException: sun.security.validator.ValidatorException
certificate_unknown
```

#### Causes
- Self-signed certificates
- Certificate chain issues
- Hostname verification failures
- Expired certificates

#### Solutions

**Verify certificate:**
```bash
# Check certificate details
openssl s_client -connect pseudonymizer.example.com:443 -servername pseudonymizer.example.com

# Check certificate expiration
echo | openssl s_client -connect pseudonymizer.example.com:443 2>/dev/null | openssl x509 -noout -dates
```

**Trust self-signed certificates:**
```bash
# Add certificate to Java truststore
keytool -import -alias pseudonymizer -keystore $JAVA_HOME/lib/security/cacerts -file pseudonymizer.crt

# Or use system property
java -Djavax.net.ssl.trustStore=/path/to/truststore.jks -jar fhir-packager.jar
```

**Disable hostname verification (development only):**
```bash
# WARNING: Only for development/testing
java -Dcom.sun.net.ssl.checkRevocation=false \
     -Dtrust_all_cert=true \
     -jar fhir-packager.jar
```

## Debugging Techniques

### Enable Verbose Logging

```bash
# Basic verbose mode
java -jar fhir-packager.jar --verbose

# Enable Spring Boot debug logging
java -Dlogging.level.org.springframework.web.reactive=DEBUG \
     -Dlogging.level.care.smith.fts.packager=DEBUG \
     -jar fhir-packager.jar
```

### Network Debugging

```bash
# Monitor HTTP traffic
# Install and use mitmproxy, Wireshark, or tcpdump

# Simple HTTP monitoring with curl
curl -v -X POST \
  -H "Content-Type: application/fhir+json" \
  --data-binary @bundle.json \
  http://localhost:8080/fhir/\$de-identify
```

### Configuration Debugging

```bash
# Show effective configuration
java -jar fhir-packager.jar --help

# Test configuration parsing
java -jar fhir-packager.jar --config-file test-config.yaml --version

# Validate YAML syntax
cat config.yaml | python -c "import yaml, sys; yaml.safe_load(sys.stdin)"
```

### Performance Debugging

```bash
# Profile memory usage
java -XX:+PrintGCDetails \
     -XX:+PrintGCTimeStamps \
     -Xloggc:gc.log \
     -jar fhir-packager.jar < bundle.json

# Monitor system resources
iostat -x 1 &
vmstat 1 &
java -jar fhir-packager.jar < bundle.json
```

## Health Check Procedures

### Service Health Verification

```bash
#!/bin/bash
# health-check.sh
PSEUDONYMIZER_URL="https://pseudonymizer.example.com"

echo "=== FHIR Packager Health Check ==="

# 1. Check service availability
echo "1. Checking service availability..."
if curl -f -s "$PSEUDONYMIZER_URL/health" > /dev/null; then
  echo "✓ Service health endpoint accessible"
else
  echo "✗ Service health endpoint failed"
  exit 1
fi

# 2. Test basic connectivity
echo "2. Testing basic connectivity..."
if timeout 10 bash -c "</dev/tcp/${PSEUDONYMIZER_URL#*//}/80"; then
  echo "✓ Network connectivity OK"
else
  echo "✗ Network connectivity failed"
  exit 1
fi

# 3. Test FHIR processing
echo "3. Testing FHIR processing..."
TEST_BUNDLE='{"resourceType":"Bundle","type":"collection","entry":[]}'
if echo "$TEST_BUNDLE" | \
   timeout 30 java -jar fhir-packager.jar \
     --pseudonymizer-url "$PSEUDONYMIZER_URL" > /dev/null 2>&1; then
  echo "✓ FHIR processing test passed"
else
  echo "✗ FHIR processing test failed"
  exit 1
fi

echo "✓ All health checks passed"
```

### Configuration Validation

```bash
#!/bin/bash
# validate-config.sh

CONFIG_FILE=${1:-application.yaml}

echo "=== Configuration Validation ==="

# 1. Check YAML syntax
echo "1. Validating YAML syntax..."
if python -c "import yaml; yaml.safe_load(open('$CONFIG_FILE'))" 2>/dev/null; then
  echo "✓ YAML syntax valid"
else
  echo "✗ YAML syntax error"
  python -c "import yaml; yaml.safe_load(open('$CONFIG_FILE'))"
  exit 1
fi

# 2. Check required fields
echo "2. Checking required configuration fields..."
URL=$(yq eval '.pseudonymizer.url' "$CONFIG_FILE" 2>/dev/null)
if [ "$URL" != "null" ] && [ -n "$URL" ]; then
  echo "✓ Pseudonymizer URL configured: $URL"
else
  echo "✗ Pseudonymizer URL missing or invalid"
  exit 1
fi

# 3. Validate URL format
echo "3. Validating URL format..."
if curl -f -s --head "$URL/health" > /dev/null 2>&1; then
  echo "✓ URL is accessible"
elif [[ $URL =~ ^https?://[a-zA-Z0-9.-]+:[0-9]+$ ]]; then
  echo "⚠ URL format valid but service not accessible"
else
  echo "✗ Invalid URL format: $URL"
  exit 1
fi

echo "✓ Configuration validation completed"
```

## Performance Tuning

### JVM Tuning

```bash
# For large bundles (high memory, optimized GC)
java -Xmx8g \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -XX:+ParallelRefProcEnabled \
     -jar fhir-packager.jar

# For many small bundles (low latency)
java -Xmx2g \
     -XX:+UseZGC \
     -XX:+UnlockExperimentalVMOptions \
     -jar fhir-packager.jar

# For memory-constrained environments
java -Xmx512m \
     -XX:+UseSerialGC \
     -XX:MaxRAMPercentage=75 \
     -jar fhir-packager.jar
```

### Network Tuning

```yaml
# Configuration for high-throughput environments
pseudonymizer:
  connect-timeout: 5s
  read-timeout: 300s
  retry:
    max-attempts: 2  # Fast failure for high throughput
    min-backoff: 100ms
    max-backoff: 5s
```

### Monitoring Setup

```bash
#!/bin/bash
# monitoring.sh - Set up monitoring for FHIR Packager

# Create monitoring directory
mkdir -p /var/log/fhir-packager

# Set up log rotation
cat > /etc/logrotate.d/fhir-packager << 'EOF'
/var/log/fhir-packager/*.log {
    daily
    rotate 30
    compress
    delaycompress
    missingok
    notifempty
    create 644 fhir-packager fhir-packager
}
EOF

# Create monitoring script
cat > /usr/local/bin/monitor-fhir-packager.sh << 'EOF'
#!/bin/bash
LOGFILE="/var/log/fhir-packager/monitoring.log"

# Monitor processing metrics
{
  echo "=== $(date) ==="
  echo "Memory usage:"
  ps aux | grep fhir-packager | grep -v grep
  echo "Disk usage:"
  df -h /tmp /var/log/fhir-packager
  echo "Network connections:"
  netstat -an | grep :8080
  echo "Processing queue:"
  ls -la /tmp/fhir-processing/ 2>/dev/null | wc -l
  echo "================"
} >> "$LOGFILE"
EOF

chmod +x /usr/local/bin/monitor-fhir-packager.sh

# Set up cron job for monitoring
echo "*/5 * * * * /usr/local/bin/monitor-fhir-packager.sh" | crontab -
```

## Getting Help

### Diagnostic Information Collection

When reporting issues, collect this diagnostic information:

```bash
#!/bin/bash
# collect-diagnostics.sh

echo "=== FHIR Packager Diagnostic Report ==="
echo "Date: $(date)"
echo "System: $(uname -a)"
echo

echo "=== Java Version ==="
java -version

echo "=== Tool Version ==="
java -jar fhir-packager.jar --version

echo "=== Configuration Test ==="
java -jar fhir-packager.jar --help

echo "=== Memory Info ==="
free -h

echo "=== Network Connectivity ==="
ping -c 3 pseudonymizer.example.com

echo "=== Recent Logs ==="
tail -50 /var/log/fhir-packager/application.log

echo "=== Environment Variables ==="
env | grep -i pseudonymizer

echo "=== End of Report ==="
```

### Support Channels

- **GitHub Issues**: [Report bugs and feature requests](https://github.com/medizininformatik-initiative/fts-next/issues)
- **Documentation**: [Official documentation](https://medizininformatik-initiative.github.io/fts-next)
- **Community**: [GitHub Discussions](https://github.com/medizininformatik-initiative/fts-next/discussions)

### Before Reporting Issues

1. **Check the logs** with `--verbose` flag
2. **Verify configuration** with validation scripts
3. **Test with minimal example** to isolate the issue
4. **Review known issues** in GitHub
5. **Collect diagnostic information** using the script above

When reporting issues, please include:
- FHIR Packager version
- Java version and JVM settings
- Operating system and version
- Configuration files (with sensitive data removed)
- Complete error messages and stack traces
- Steps to reproduce the issue
- Expected vs. actual behavior