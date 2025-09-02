# Configuration Reference

The FHIR Packager supports flexible configuration through a three-tier system with clear precedence rules.

## Configuration Hierarchy

Configuration is applied in the following order (highest to lowest priority):

1. **Command Line Arguments** (highest priority)
2. **Environment Variables**
3. **YAML Configuration Files** (lowest priority)

This allows for flexible deployment patterns where base configuration is set in files, environment-specific overrides use environment variables, and runtime adjustments use command-line arguments.

## Command Line Arguments

### Basic Options

| Argument | Description | Default | Example |
|----------|-------------|---------|---------|
| `--pseudonymizer-url URL` | FHIR Pseudonymizer service URL | `http://localhost:8080` | `--pseudonymizer-url https://pseudonymizer.example.com` |
| `--timeout SECONDS` | Connection and read timeout | `30` | `--timeout 60` |
| `--retries NUMBER` | Maximum retry attempts | `3` | `--retries 5` |
| `--verbose` | Enable verbose logging | `false` | `--verbose` |
| `--config-file PATH` | External YAML config file | - | `--config-file ~/my-config.yaml` |
| `--help` | Show help message | - | `--help` |
| `--version` | Show version information | - | `--version` |

### Advanced Options

::: info Future Enhancement
Additional CLI options may be added for fine-grained control of retry behavior, health checks, and performance tuning.
:::

### Examples

```bash
# Basic usage with custom URL
java -jar fhir-packager.jar --pseudonymizer-url https://pseudonymizer.prod.example.com

# Production settings with retries and timeout
java -jar fhir-packager.jar \
  --pseudonymizer-url https://pseudonymizer.prod.example.com \
  --timeout 120 \
  --retries 5 \
  --verbose

# Using external config file
java -jar fhir-packager.jar --config-file /etc/fhir-packager/production.yaml

# Config file with tilde expansion
java -jar fhir-packager.jar --config-file ~/configs/fhir-packager.yaml
```

## Environment Variables

All configuration options can be set via environment variables using the pattern `PSEUDONYMIZER_*`:

### Service Configuration

| Variable | Description | Type | Default |
|----------|-------------|------|---------|
| `PSEUDONYMIZER_URL` | Service URL | String | `http://localhost:8080` |
| `PSEUDONYMIZER_CONNECT_TIMEOUT` | Connection timeout (seconds) | Integer | `30` |
| `PSEUDONYMIZER_READ_TIMEOUT` | Read timeout (seconds) | Integer | `30` |
| `PSEUDONYMIZER_HEALTH_CHECK_ENABLED` | Enable health check | Boolean | `true` |

### Retry Configuration

| Variable | Description | Type | Default |
|----------|-------------|------|---------|
| `PSEUDONYMIZER_RETRY_MAX_ATTEMPTS` | Maximum retry attempts | Integer | `3` |
| `PSEUDONYMIZER_RETRY_MIN_BACKOFF` | Minimum backoff (seconds) | Integer | `1` |
| `PSEUDONYMIZER_RETRY_MAX_BACKOFF` | Maximum backoff (seconds) | Integer | `10` |
| `PSEUDONYMIZER_RETRY_BACKOFF_MULTIPLIER` | Backoff multiplier | Double | `2.0` |
| `PSEUDONYMIZER_RETRY_JITTER` | Jitter factor (0.0-1.0) | Double | `0.1` |

### Examples

```bash
# Development environment
export PSEUDONYMIZER_URL=http://localhost:8080
export PSEUDONYMIZER_CONNECT_TIMEOUT=30
export PSEUDONYMIZER_READ_TIMEOUT=60

# Production environment
export PSEUDONYMIZER_URL=https://pseudonymizer.prod.example.com
export PSEUDONYMIZER_CONNECT_TIMEOUT=45
export PSEUDONYMIZER_READ_TIMEOUT=120
export PSEUDONYMIZER_RETRY_MAX_ATTEMPTS=5
export PSEUDONYMIZER_HEALTH_CHECK_ENABLED=true

# High-reliability environment
export PSEUDONYMIZER_RETRY_MAX_ATTEMPTS=10
export PSEUDONYMIZER_RETRY_MIN_BACKOFF=2
export PSEUDONYMIZER_RETRY_MAX_BACKOFF=60
export PSEUDONYMIZER_RETRY_BACKOFF_MULTIPLIER=1.5
export PSEUDONYMIZER_RETRY_JITTER=0.2
```

## YAML Configuration Files

### File Locations

The tool searches for configuration files in the following order:

1. File specified by `--config-file` argument
2. `./application.yaml` (current directory)
3. `~/.config/fhir-packager/application.yaml` (user config)
4. `/etc/fhir-packager/application.yaml` (system config)

### Basic Configuration

```yaml
# Basic configuration
pseudonymizer:
  url: http://localhost:8080
  connect-timeout: 30s
  read-timeout: 60s
  health-check-enabled: true

logging:
  level:
    care.smith.fts.packager: INFO
```

### Complete Configuration Example

```yaml
# Complete configuration with all options
pseudonymizer:
  # Service endpoint configuration
  url: ${PSEUDONYMIZER_URL:http://localhost:8080}
  connect-timeout: 45s
  read-timeout: 120s
  health-check-enabled: true
  
  # Retry configuration
  retry:
    max-attempts: 5
    min-backoff: 2s
    max-backoff: 30s
    backoff-multiplier: 2.0
    jitter: 0.1
    
  # HTTP client configuration
  http:
    max-connections: 10
    max-connections-per-route: 5
    connection-time-to-live: 60s
    
# Logging configuration
logging:
  level:
    root: WARN
    care.smith.fts.packager: INFO
    org.springframework.web.reactive.function.client: DEBUG
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n"
    
# Spring Boot actuator (if enabled)
management:
  endpoint:
    health:
      show-details: when-authorized
  endpoints:
    web:
      exposure:
        include: health,info,metrics
```

### Environment-Specific Configurations

#### Development

```yaml
# development.yaml
pseudonymizer:
  url: http://localhost:8080
  connect-timeout: 10s
  read-timeout: 30s
  health-check-enabled: false  # Skip for faster local testing
  retry:
    max-attempts: 1  # Fail fast in development

logging:
  level:
    care.smith.fts.packager: DEBUG
    org.springframework.web.reactive: DEBUG
```

#### Testing

```yaml
# testing.yaml
pseudonymizer:
  url: http://pseudonymizer-test:8080
  connect-timeout: 5s
  read-timeout: 15s
  health-check-enabled: true
  retry:
    max-attempts: 2
    min-backoff: 100ms
    max-backoff: 1s

logging:
  level:
    root: WARN
    care.smith.fts.packager: INFO
```

#### Production

```yaml
# production.yaml
pseudonymizer:
  url: ${PSEUDONYMIZER_URL}
  connect-timeout: 45s
  read-timeout: 180s
  health-check-enabled: true
  retry:
    max-attempts: 10
    min-backoff: 5s
    max-backoff: 300s
    backoff-multiplier: 1.5
    jitter: 0.2

logging:
  level:
    root: WARN
    care.smith.fts.packager: INFO
  file:
    name: /var/log/fhir-packager/application.log
    max-size: 100MB
    max-history: 30
    
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

## Configuration Validation

The tool performs validation at startup to ensure configuration is valid:

### Validation Rules

- **URL Format**: Must be a valid HTTP/HTTPS URL
- **Timeouts**: Must be positive integers or duration strings
- **Retry Attempts**: Must be >= 0
- **Backoff Values**: Must be positive
- **Jitter**: Must be between 0.0 and 1.0

### Validation Examples

```bash
# Valid configurations
java -jar fhir-packager.jar --pseudonymizer-url https://example.com --timeout 30
java -jar fhir-packager.jar --retries 0  # Disable retries

# Invalid configurations (will cause startup failure)
java -jar fhir-packager.jar --pseudonymizer-url invalid-url      # Invalid URL
java -jar fhir-packager.jar --timeout -1                        # Negative timeout
java -jar fhir-packager.jar --retries -1                        # Negative retries
```

## Advanced Configuration

### Custom HTTP Client Configuration

::: info Future Enhancement
Advanced HTTP client configuration (connection pooling, TLS settings, proxy support) may be added in future versions.
:::

```yaml
# Future configuration options
pseudonymizer:
  http:
    # Connection pooling
    max-connections: 20
    max-connections-per-route: 10
    connection-time-to-live: 300s
    
    # TLS configuration
    tls:
      enabled: true
      verify-hostname: true
      keystore: /path/to/keystore.p12
      keystore-password: ${KEYSTORE_PASSWORD}
      truststore: /path/to/truststore.p12
      truststore-password: ${TRUSTSTORE_PASSWORD}
    
    # Proxy configuration
    proxy:
      host: proxy.example.com
      port: 8080
      username: ${PROXY_USERNAME}
      password: ${PROXY_PASSWORD}
```

### Performance Tuning

```yaml
# Performance-focused configuration
pseudonymizer:
  url: https://pseudonymizer.example.com
  connect-timeout: 10s
  read-timeout: 300s  # Long timeout for large bundles
  retry:
    max-attempts: 3
    min-backoff: 1s
    max-backoff: 30s
    
  http:
    max-connections: 50
    max-connections-per-route: 20
    connection-time-to-live: 600s

# JVM tuning (via environment or command line)
# -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

### Monitoring and Observability

```yaml
# Enhanced logging for monitoring
logging:
  level:
    care.smith.fts.packager: INFO
    care.smith.fts.packager.client: DEBUG  # HTTP client details
  pattern:
    console: "%d{ISO8601} [%thread] %-5level [%X{bundleId}] %logger{36} - %msg%n"
    
# Metrics collection (if enabled)
management:
  metrics:
    export:
      prometheus:
        enabled: true
        step: 30s
  endpoint:
    metrics:
      enabled: true
    prometheus:
      enabled: true
```

## Configuration Best Practices

### Security

1. **Use environment variables for sensitive data**:
   ```yaml
   pseudonymizer:
     url: ${PSEUDONYMIZER_URL}
     auth:
       username: ${PSEUDONYMIZER_USERNAME}
       password: ${PSEUDONYMIZER_PASSWORD}
   ```

2. **Secure configuration files**:
   ```bash
   chmod 600 /etc/fhir-packager/application.yaml
   chown fhir-packager:fhir-packager /etc/fhir-packager/application.yaml
   ```

### Reliability

1. **Configure appropriate timeouts**:
   ```yaml
   pseudonymizer:
     connect-timeout: 30s    # Quick connection establishment
     read-timeout: 300s      # Allow for large bundle processing
   ```

2. **Set up retry logic for production**:
   ```yaml
   pseudonymizer:
     retry:
       max-attempts: 5       # Balance reliability vs. processing time
       min-backoff: 2s       # Avoid overwhelming the service
       max-backoff: 60s      # Cap maximum delay
       jitter: 0.2           # Prevent thundering herd
   ```

### Performance

1. **Optimize for your workload**:
   ```yaml
   # For many small bundles
   pseudonymizer:
     connect-timeout: 5s
     read-timeout: 30s
     retry:
       max-attempts: 3
       
   # For few large bundles
   pseudonymizer:
     connect-timeout: 30s
     read-timeout: 600s
     retry:
       max-attempts: 1  # Large bundles are expensive to retry
   ```

2. **Monitor resource usage**:
   ```bash
   # Monitor memory usage
   java -XX:+PrintGCDetails -Xloggc:gc.log -jar fhir-packager.jar
   
   # Monitor network connections
   netstat -an | grep :8080
   ```

## Troubleshooting Configuration

### Common Configuration Issues

| Issue | Symptom | Solution |
|-------|---------|----------|
| Invalid URL format | `IllegalArgumentException` at startup | Check URL format (must include protocol) |
| Service unreachable | Connection timeouts | Verify network connectivity and URL |
| Wrong timeout format | Configuration parsing error | Use integer seconds or duration strings (`30s`) |
| File not found | Configuration file errors | Check file paths and permissions |

### Debugging Configuration

```bash
# Show effective configuration
java -jar fhir-packager.jar --help  # Shows current defaults

# Test configuration without processing
echo '{}' | java -jar fhir-packager.jar --verbose 2>&1 | head -20

# Validate specific config file
java -jar fhir-packager.jar --config-file my-config.yaml --help
```

### Configuration Examples by Use Case

#### CI/CD Pipeline

```yaml
# Optimized for automated testing
pseudonymizer:
  url: http://pseudonymizer-test:8080
  connect-timeout: 10s
  read-timeout: 60s
  health-check-enabled: false
  retry:
    max-attempts: 2
    min-backoff: 500ms
    max-backoff: 2s
```

#### Batch Processing

```yaml
# Optimized for large-scale batch processing
pseudonymizer:
  url: https://pseudonymizer.prod.example.com
  connect-timeout: 30s
  read-timeout: 600s  # Large bundles may take time
  retry:
    max-attempts: 5
    min-backoff: 10s
    max-backoff: 300s
    jitter: 0.3  # Spread out retry attempts
```

#### Real-time Processing

```yaml
# Optimized for low-latency processing
pseudonymizer:
  url: https://pseudonymizer-realtime.example.com
  connect-timeout: 5s
  read-timeout: 30s
  retry:
    max-attempts: 2  # Quick failure for real-time use
    min-backoff: 100ms
    max-backoff: 1s
```