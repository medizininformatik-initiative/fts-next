# FHIR Packager

[![Build](https://img.shields.io/github/actions/workflow/status/medizininformatik-initiative/fts-next/build.yml?logo=refinedgithub&logoColor=white)](https://github.com/medizininformatik-initiative/fts-next/actions/workflows/build.yml)
[![Documentation](https://img.shields.io/website?url=https%3A%2F%2Fmedizininformatik-initiative.github.io%2Ffts-next&up_message=online&up_color=blue&down_message=offline&logo=readthedocs&logoColor=white&label=docs)](https://medizininformatik-initiative.github.io/fts-next)

A standalone command-line tool for pseudonymizing FHIR Bundles through a REST service. Part of the [FTSnext](https://github.com/medizininformatik-initiative/fts-next) ecosystem.

## Overview

The FHIR Packager is a lightweight CLI tool that:
- Reads FHIR Bundle JSON from stdin
- Sends it to a FHIR Pseudonymizer REST service for de-identification
- Outputs the pseudonymized Bundle to stdout
- Provides clean separation of data (stdin/stdout) and logging (stderr)

This tool is designed for command-line processing workflows and can be easily integrated into data processing pipelines.

## Quick Start

```bash
# Build the executable JAR
mvn clean package

# Basic usage - pipe a FHIR Bundle through the tool
cat patient-bundle.json | java -jar target/fhir-packager-*.jar > pseudonymized-bundle.json

# With custom service URL and timeout
cat patient-bundle.json | java -jar target/fhir-packager-*.jar \
  --pseudonymizer-url http://localhost:8080 \
  --timeout 60 > pseudonymized-bundle.json

# With verbose logging
cat patient-bundle.json | java -jar target/fhir-packager-*.jar --verbose > pseudonymized-bundle.json 2>debug.log
```

## Features

- **Standalone CLI**: No server dependencies, runs as a single command
- **Flexible Configuration**: CLI arguments, environment variables, or YAML files
- **Robust Error Handling**: Meaningful exit codes and error messages
- **Retry Logic**: Configurable retry with exponential backoff and jitter
- **Health Checks**: Optional service availability verification
- **Performance Optimized**: Handles large bundles efficiently with reactive streams
- **Security**: HTTPS support, no sensitive data logging
- **Testing**: Comprehensive unit and integration test suite

## Installation

### Prerequisites

- Java 21 or higher
- Maven 3.6+ (for building from source)
- A running FHIR Pseudonymizer service

### Build from Source

```bash
# Clone the repository (if not already done)
git clone https://github.com/medizininformatik-initiative/fts-next.git
cd fts-next/fhir-packager

# Build the executable JAR
mvn clean package

# The executable JAR will be created at:
# target/fhir-packager-*.jar
```

### Docker Usage

```bash
# Build Docker image (if Dockerfile exists)
docker build -t fhir-packager .

# Run with Docker
docker run -i fhir-packager < input.json > output.json
```

## Configuration

The tool supports three-tier configuration (highest to lowest priority):

1. **Command Line Arguments** (highest priority)
2. **Environment Variables**
3. **YAML Configuration File** (lowest priority)

### Command Line Arguments

```bash
java -jar fhir-packager-*.jar [OPTIONS]

Options:
  --pseudonymizer-url URL    Service URL (default: http://localhost:8080)
  --timeout SECONDS          Connection and read timeout (default: 30)
  --retries NUMBER           Max retry attempts (default: 3)
  --verbose                  Enable verbose logging
  --config-file PATH         External YAML config file (supports ~ expansion)
  --help                     Show help message
  --version                  Show version information
```

### Environment Variables

```bash
export PSEUDONYMIZER_URL=http://pseudonymizer:8080
export PSEUDONYMIZER_CONNECT_TIMEOUT=30
export PSEUDONYMIZER_READ_TIMEOUT=60
export PSEUDONYMIZER_RETRY_MAX_ATTEMPTS=5
export PSEUDONYMIZER_HEALTH_CHECK_ENABLED=true
```

### YAML Configuration

Create `application.yaml`:

```yaml
pseudonymizer:
  url: http://localhost:8080
  connect-timeout: 30s
  read-timeout: 60s
  health-check-enabled: true
  retry:
    max-attempts: 3
    min-backoff: 1s
    max-backoff: 10s
    backoff-multiplier: 2.0
    jitter: 0.1

logging:
  level:
    care.smith.fts.packager: INFO
```

## Usage Examples

### Basic Pipeline Usage

```bash
# Simple pseudonymization
cat bundle.json | java -jar fhir-packager-*.jar > pseudonymized.json

# With error handling
if cat bundle.json | java -jar fhir-packager-*.jar > result.json 2>error.log; then
  echo "Success: Pseudonymized bundle saved to result.json"
else
  echo "Failed: Check error.log for details"
fi
```

### Integration with curl

```bash
# Fetch bundle from FHIR server, pseudonymize, and post to research server
curl -s "http://fhir-server/Bundle/123" | \
  java -jar fhir-packager-*.jar | \
  curl -X POST -H "Content-Type: application/fhir+json" \
    -d @- "http://research-server/Bundle"
```

### Batch Processing

```bash
# Process multiple bundles
for bundle in bundles/*.json; do
  echo "Processing $bundle..."
  cat "$bundle" | java -jar fhir-packager-*.jar > "pseudonymized/$(basename "$bundle")"
done
```

### Configuration File Usage

```bash
# Create custom config
cat > my-config.yaml << EOF
pseudonymizer:
  url: https://production-pseudonymizer.example.com
  connect-timeout: 45s
  read-timeout: 120s
  retry:
    max-attempts: 5
EOF

# Use custom config
cat bundle.json | java -jar fhir-packager-*.jar --config-file my-config.yaml > result.json
```

## Error Handling

The tool uses specific exit codes to indicate different error conditions:

- `0`: Success
- `1`: General error (network issues, service errors)
- `2`: Invalid command line arguments
- `3`: Invalid FHIR Bundle format

### Error Examples

```bash
# Check exit code
cat invalid-bundle.json | java -jar fhir-packager-*.jar
echo $?  # Will be 3 for invalid FHIR Bundle

# Handle errors in scripts
if ! cat bundle.json | java -jar fhir-packager-*.jar > result.json; then
  case $? in
    1) echo "Service error or network issue" ;;
    2) echo "Invalid arguments" ;;
    3) echo "Invalid FHIR Bundle format" ;;
  esac
fi
```

## Performance Considerations

- **Memory**: Designed to handle bundles up to several GB with < 2GB memory overhead
- **Processing Time**: Adds < 1 second overhead for typical bundles
- **Streaming**: Uses reactive streams to avoid loading entire bundles in memory
- **Network**: Configurable timeouts and retry logic for unreliable networks

### Performance Tips

```bash
# Increase JVM memory for very large bundles
java -Xmx4g -jar fhir-packager-*.jar < large-bundle.json > result.json

# Adjust timeouts for slow networks
java -jar fhir-packager-*.jar --timeout 300 < bundle.json > result.json

# Monitor memory usage
java -XX:+PrintGCDetails -jar fhir-packager-*.jar < bundle.json > result.json
```

## Architecture

The tool is built using:
- **Spring Boot**: Application framework (without web server)
- **Spring WebFlux**: Reactive HTTP client for API calls
- **Picocli**: Command-line interface and argument parsing
- **HAPI FHIR**: FHIR resource handling and JSON parsing

### Design Principles

- **Clean I/O Separation**: Data flows through stdin/stdout, logs go to stderr
- **Fail-Fast**: Proper validation with meaningful error messages
- **Configurable**: Multiple configuration layers for different environments
- **Testable**: Comprehensive test suite with mocked external dependencies
- **Resilient**: Retry logic and timeout handling for production use

## Development

### Running Tests

```bash
# Unit tests only
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify

# Specific test
mvn test -Dtest=PackagerCommandTest#testValidBundleProcessing
```

### Building

```bash
# Clean build
mvn clean package

# Skip tests
mvn clean package -DskipTests

# Build with specific profile
mvn clean package -Pproduction
```

### Code Style

The project follows Google Java Style Guide enforced by Checkstyle:

```bash
# Check style
mvn checkstyle:check

# Format code (if configured)
mvn fmt:format
```

## Integration with FTS Ecosystem

While standalone, this module:
- Uses shared utilities from the FTS `util` module
- Follows same configuration patterns as FTS agents
- Can be integrated into FTS workflows as a processing step
- Supports the same authentication and security patterns

## Troubleshooting

### Common Issues

**Service Connection Refused**
```
Error: Connection refused to http://localhost:8080
Solution: Ensure the pseudonymizer service is running and accessible
```

**Invalid FHIR Bundle**
```
Error: Invalid FHIR Bundle format
Solution: Validate input JSON with FHIR validator before processing
```

**Timeout Errors**
```
Error: Read timeout after 30000ms
Solution: Increase timeout with --timeout or configure in YAML
```

**Memory Issues**
```
Error: OutOfMemoryError
Solution: Increase JVM memory with -Xmx flag
```

### Debug Mode

```bash
# Enable verbose logging
java -jar fhir-packager-*.jar --verbose < bundle.json > result.json 2>debug.log

# Enable Spring Boot debug logging
java -Dlogging.level.org.springframework=DEBUG -jar fhir-packager-*.jar
```

### Health Check

```bash
# Test service availability
java -jar fhir-packager-*.jar --help  # Shows if config is valid
curl -f http://localhost:8080/health  # Check service health directly
```

## Contributing

1. Follow the [FTS Contributing Guidelines](../CONTRIBUTING.md)
2. Ensure all tests pass: `mvn clean verify`
3. Follow Google Java Style Guide
4. Add tests for new functionality
5. Update documentation for new features

## Links

- [FTSnext Documentation](https://medizininformatik-initiative.github.io/fts-next)
- [FHIR R4 Specification](https://hl7.org/fhir/R4/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [GitHub Repository](https://github.com/medizininformatik-initiative/fts-next)

## License

Licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).