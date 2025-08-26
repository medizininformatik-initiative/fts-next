# FHIR Packager Module - Claude Assistant Guidelines

This file provides guidance to Claude Code when working with the `fhir-packager` module.

## Module Purpose

The `fhir-packager` is a standalone CLI tool that:
- Reads a FHIR Bundle from stdin
- Sends it to a FHIR Pseudonymizer REST service for pseudonymization
- Outputs the pseudonymized Bundle to stdout

This module is designed for command-line processing workflows and integration with other tools.

## Architecture Overview

### Core Components
- **FhirPackagerApplication**: Spring Boot main application with CommandLineRunner
- **PackagerCommand**: Picocli command handling CLI arguments and orchestration
- **PseudonymizerClient**: WebClient wrapper for FHIR Pseudonymizer REST API calls
- **BundleProcessor**: Handles FHIR Bundle reading, processing, and writing
- **PseudonymizerConfig**: Configuration properties for the external service

### Design Principles
- **Clean separation**: stdin/stdout for data, stderr for logging/errors
- **Fail-fast**: Proper error handling with meaningful exit codes
- **Configurable**: Support CLI args, env vars, and config files
- **Testable**: Comprehensive unit and integration tests
- **Resilient**: Proper timeout and retry handling for external API calls

## Key Dependencies

- **Spring Boot**: Application framework (without web server)
- **Spring WebFlux**: Reactive HTTP client for API calls
- **Picocli**: Command-line interface and argument parsing
- **HAPI FHIR**: FHIR resource handling and JSON parsing
- **FTS Util**: Shared utilities from parent project (WebClient, error handling)
- **FTS API**: Common interfaces and data models

## Configuration

The module supports configuration through:
1. CLI arguments (highest priority)
2. Environment variables
3. application.yaml file (lowest priority)

Key configuration options:
- Pseudonymizer service URL
- Connection timeouts
- Retry policies
- Authentication settings (if needed)
- Logging levels

## Testing Strategy

- **Unit Tests**: Business logic, configuration, CLI parsing
- **Integration Tests**: End-to-end with WireMock for external service
- **Connection Scenario Tests**: Network failures, timeouts, retries

## Build and Packaging

- Spring Boot executable JAR with all dependencies
- Optional Docker image for containerized environments
- Makefile integration for consistent build process

## Usage Patterns

```bash
# Basic usage
cat input.json | java -jar fhir-packager.jar > output.json

# With custom configuration
cat input.json | java -jar fhir-packager.jar --pseudonymizer-url http://localhost:8080 > output.json

# With environment variables
export PSEUDONYMIZER_URL=http://pseudonymizer:8080
cat input.json | java -jar fhir-packager.jar > output.json
```

## Development Guidelines

### Code Style
- Follow Google Java Style Guide (enforced by checkstyle)
- Use Lombok for boilerplate reduction (@Slf4j, @RequiredArgsConstructor, etc.)
- Prefer immutable configuration classes (records when appropriate)
- Use reactive patterns (Mono/Flux) for HTTP calls

### Error Handling
- Use proper exit codes (0 = success, 1 = general error, 2 = invalid arguments)
- Log errors to stderr, never to stdout
- Provide meaningful error messages for common failure scenarios
- Handle network timeouts and connection failures gracefully

### Testing
- Use @SpringBootTest for integration tests
- Use WireMock for external service mocking
- Test both happy path and error conditions
- Verify proper CLI argument parsing and validation

### Security Considerations
- Never log sensitive data (patient information, auth tokens)
- Support secure communication (HTTPS, authentication headers)
- Validate input bundles before processing
- Handle large bundles without memory issues

## External Dependencies

### FHIR Pseudonymizer Service
- .NET/C# application running as REST service
- Endpoint: `/fhir/$de-identify` (POST)
- Input: FHIR Bundle in JSON format
- Output: Pseudonymized FHIR Bundle
- Content-Type: `application/fhir+json`

The service must be running and accessible at the configured URL.

## Common Issues and Solutions

1. **Large Bundle Handling**: Use streaming where possible, configure appropriate memory limits
2. **Network Timeouts**: Configure reasonable timeouts based on bundle size and network conditions
3. **Invalid Input**: Validate FHIR Bundle structure before sending to service
4. **Service Unavailability**: Implement retry logic with exponential backoff
5. **Memory Issues**: Use reactive streams to avoid loading entire bundles in memory

## Integration with FTS Ecosystem

While standalone, this module follows FTS project conventions:
- Uses shared utilities from `util` module
- Follows same configuration patterns
- Uses same logging and error handling approaches
- Can be integrated into FTS workflows as needed