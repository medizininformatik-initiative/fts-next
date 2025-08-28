# FHIR Packager - Implementation Documentation

## Overview

The FHIR Packager is a standalone CLI tool that pseudonymizes FHIR Bundles by integrating with the external FHIR Pseudonymizer REST service. It follows a streaming approach to handle large bundles efficiently while maintaining clean separation between data flow and logging.

## Architecture

### High-Level Flow
```
stdin → Bundle Reading → FHIR Pseudonymizer API → Bundle Writing → stdout
                ↓                     ↓                    ↓
             logging              error handling       logging
                ↓                     ↓                    ↓  
              stderr                stderr              stderr
```

### Core Components

#### 1. FhirPackagerApplication
- **Purpose**: Spring Boot entry point with CommandLineRunner
- **Responsibilities**: 
  - Bootstrap Spring context
  - Delegate to PackagerCommand
  - Handle application-level exceptions
- **Key Features**:
  - Disable web server (CLI application)
  - Configure logging to stderr only
  - Set proper exit codes

#### 2. PackagerCommand  
- **Purpose**: Picocli command implementation
- **Responsibilities**:
  - Parse CLI arguments
  - Validate configuration
  - Orchestrate the packaging process
- **Configuration Options**:
  - `--pseudonymizer-url`: Service endpoint URL
  - `--timeout`: Request timeout in seconds
  - `--retries`: Number of retry attempts
  - `--verbose`: Enable debug logging
  - `--config-file`: External configuration file path

#### 3. PseudonymizerClient
- **Purpose**: REST API client for FHIR Pseudonymizer
- **Responsibilities**:
  - HTTP communication with pseudonymizer service
  - Request/response handling
  - Error mapping and retry logic
- **Key Features**:
  - Reactive WebClient implementation
  - Configurable timeouts and retries
  - Proper error handling and mapping
  - Content-Type: application/fhir+json

#### 4. BundleProcessor
- **Purpose**: FHIR Bundle processing coordination
- **Responsibilities**:
  - Read JSON from stdin
  - Parse FHIR Bundle using HAPI FHIR
  - Coordinate with PseudonymizerClient
  - Serialize and write to stdout
- **Key Features**:
  - Streaming JSON processing
  - FHIR Bundle validation
  - Memory-efficient handling

#### 5. PseudonymizerConfig
- **Purpose**: Configuration properties
- **Responsibilities**:
  - Centralize configuration management
  - Support multiple configuration sources
  - Provide validation and defaults
- **Configuration Sources** (priority order):
  1. CLI arguments
  2. Environment variables
  3. application.yaml
  4. Default values

## Implementation Phases

### Phase 1: Basic Structure and Configuration
**Goal**: Set up the module foundation and configuration system

**Tasks**:
1. Create `PseudonymizerConfig` with basic properties:
   - `pseudonymizer.url` (default: http://localhost:8080)
   - `pseudonymizer.timeout` (default: PT30S)
   - `pseudonymizer.retries` (default: 3)

2. Create `FhirPackagerApplication` main class:
   - Configure Spring Boot with no web server
   - Set up logging configuration (stderr only)
   - Implement CommandLineRunner interface

3. Create basic `application.yaml` with default settings

4. Add unit tests for configuration loading and validation

**Acceptance Criteria**:
- Module builds successfully
- Configuration loads from all sources
- Basic Spring Boot application starts and exits cleanly

### Phase 2: CLI Interface and Argument Parsing
**Goal**: Implement command-line interface using Picocli

**Tasks**:
1. Create `PackagerCommand` with Picocli annotations:
   - Define CLI options and validation
   - Implement argument parsing and validation
   - Handle help text and usage information

2. Integrate PackagerCommand with FhirPackagerApplication:
   - Configure Picocli Spring integration
   - Handle command execution and exit codes
   - Add global exception handling

3. Add CLI argument validation:
   - URL format validation
   - Timeout value ranges
   - File path validation for config files

4. Add unit tests for CLI parsing and validation

**Acceptance Criteria**:
- All CLI arguments parse correctly
- Help text displays properly
- Invalid arguments show meaningful errors
- Exit codes are correct (0=success, 1=error, 2=invalid args)

### Phase 3: FHIR Bundle Processing
**Goal**: Implement FHIR Bundle reading, parsing, and writing

**Tasks**:
1. Create `BundleProcessor` class:
   - Read JSON from System.in using streaming
   - Parse FHIR Bundle using HAPI FHIR parser
   - Validate bundle structure
   - Serialize bundle back to JSON for output

2. Implement error handling:
   - Invalid JSON format
   - Non-bundle FHIR resources
   - Empty or malformed input
   - Large bundle memory management

3. Add logging and metrics:
   - Bundle size and resource count
   - Processing duration
   - Error conditions

4. Add comprehensive unit tests:
   - Valid bundle processing
   - Invalid input handling
   - Memory usage with large bundles

**Acceptance Criteria**:
- Can read and parse various FHIR Bundle formats
- Handles invalid input gracefully with clear error messages
- Memory usage remains stable with large bundles
- Output format matches input format

### Phase 4: REST API Integration
**Goal**: Implement communication with FHIR Pseudonymizer service

**Current Status**: Interface created, implementation needed

**Step-by-Step Implementation Plan**:

#### Step 1: Create PseudonymizerClientImpl (2-3 hours)
1. **Create implementation class**:
   - Location: `src/main/java/care/smith/fts/packager/service/PseudonymizerClientImpl.java`
   - Implement `PseudonymizerClient` interface
   - Add `@Service` and `@Slf4j` annotations
   - Inject `PseudonymizerConfig` and `WebClient.Builder`

2. **Configure WebClient**:
   ```java
   WebClient.builder()
       .baseUrl(config.url())
       .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/fhir+json")
       .defaultHeader(HttpHeaders.ACCEPT, "application/fhir+json")
       .clientConnector(new ReactorClientHttpConnector(
           HttpClient.create()
               .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())
               .doOnConnected(conn -> conn
                   .addHandlerLast(new ReadTimeoutHandler(config.readTimeout().toSeconds()))
               )
       ))
       .codecs(configurer -> configurer
           .defaultCodecs()
           .maxInMemorySize(100 * 1024 * 1024) // 100MB
       )
       .build();
   ```

3. **Implement core methods**:
   - `pseudonymize(Bundle bundle)`: POST to `/fhir/$de-identify`
   - `checkHealth()`: GET to health endpoint with response time tracking
   - Error mapping and timeout handling

#### Step 2: Add Retry Logic with Exponential Backoff (2 hours)
1. **Configure retry strategy**:
   ```java
   Retry.backoff(
       config.retry().maxAttempts(),
       config.retry().initialBackoff()
   )
   .maxBackoff(config.retry().maxBackoff())
   .jitter(0.5)
   .filter(this::isRetryable)
   .doBeforeRetry(signal -> 
       log.warn("Retry attempt {} after {} ms", 
           signal.totalRetries() + 1, 
           signal.totalRetriesInARow())
   )
   ```

2. **Define retryable vs non-retryable errors**:
   - Retryable: 503, 502, timeout, connection errors
   - Non-retryable: 400, 401, 403, 404, parsing errors

#### Step 3: Implement Comprehensive Error Handling (2 hours)
1. **Create error mapping strategy**:
   - WebClientResponseException → PseudonymizerResponseException
   - TimeoutException → PseudonymizerTimeoutException  
   - ConnectException → PseudonymizerConnectionException
   - Include detailed error context and correlation IDs

2. **Handle HTTP status codes appropriately**:
   - 400: Bad Request → validation error with details
   - 401/403: Authentication → auth exception
   - 500: Server Error → service exception (retryable)
   - 503: Service Unavailable → temporary failure (retryable)

#### Step 4: Integrate with BundleProcessor (2 hours)
1. **Replace identity transform in `processBundle(Bundle bundle)`**:
   - Inject `PseudonymizerClient`
   - Call `pseudonymizerClient.pseudonymize(bundle)`
   - Add health check verification if enabled
   - Handle reactive response with proper error mapping

2. **Add progress tracking**:
   - Log bundle size and processing metrics
   - Track network call duration
   - Add performance monitoring

#### Step 5: Create WireMock Integration Tests (3 hours)
1. **Create `PseudonymizerClientIntegrationTest`**:
   - Setup WireMockServer with dynamic port
   - Configure Spring test context

2. **Test scenarios**:
   - Successful pseudonymization flow
   - Retry scenarios (503 → 503 → 200)
   - Timeout handling (connect vs read timeouts)
   - Error responses (400, 401, 500) with retry behavior
   - Large bundle handling

#### Step 6: Add End-to-End Docker Integration Tests (2 hours)
1. **Create `PackagerE2ETest` with Testcontainers**:
   - Start FHIR Pseudonymizer container
   - Configure random port mapping
   - Test with real service

2. **Test scenarios**:
   - Load test bundle → process → verify pseudonymization
   - Container failure and recovery
   - Service unavailability handling

#### Step 7: Add Authentication Support (Optional, 2 hours)
1. **Extend PseudonymizerConfig**:
   ```java
   record AuthConfig(
       AuthType type,  // NONE, BASIC, BEARER, CLIENT_CERT
       String username,
       String password,
       String token
   )
   ```

2. **Configure WebClient authentication**:
   - Basic Auth: `addBasicAuth(username, password)`
   - Bearer Token: `addBearerToken(token)`
   - Test authentication scenarios

#### Step 8: Performance and Load Testing (2 hours)
1. **Create performance tests**:
   - Test with 1MB, 10MB, 100MB bundles
   - Measure memory usage and processing time
   - Verify streaming behavior

2. **Memory profiling**:
   - Test with -Xmx512m constraint
   - Verify no memory leaks
   - Check connection pool limits

#### Step 9: Documentation and Polish (1 hour)
1. **Update documentation**:
   - Add configuration examples
   - Document error codes and meanings
   - Create troubleshooting guide

2. **Add operational features**:
   - Structured logging with MDC
   - Metrics collection hooks
   - Graceful shutdown handling

#### Step 10: Final Integration and Testing (2 hours)
1. **Run full test suite**: `mvn clean verify`
2. **Manual testing scenarios** with docker-compose
3. **Performance validation**: < 1s overhead, < 2GB memory

**Testing Matrix**:

| Scenario | Unit Test | Integration Test | E2E Test |
|----------|-----------|-----------------|----------|
| Successful pseudonymization | ✓ | ✓ | ✓ |
| Network timeout | ✓ | ✓ | ✓ |
| Service unavailable (503) | ✓ | ✓ | ✓ |
| Invalid bundle (400) | ✓ | ✓ | ✓ |
| Retry with backoff | ✓ | ✓ | - |
| Large bundle handling | ✓ | ✓ | ✓ |
| Health check | ✓ | ✓ | ✓ |

**Acceptance Criteria**:
- Successfully communicates with FHIR Pseudonymizer service
- Handles all common HTTP error scenarios with proper exit codes
- Retry logic works correctly with exponential backoff
- Integration tests pass with both mocked and real service
- Performance meets requirements (< 1s overhead, < 2GB memory)
- Health check functionality operational
- Comprehensive error handling and logging

### Phase 5: End-to-End Integration
**Goal**: Complete integration and comprehensive testing

**Tasks**:
1. Integrate all components in `PackagerCommand`:
   - Coordinate BundleProcessor and PseudonymizerClient
   - Handle the complete processing pipeline
   - Manage error propagation and logging

2. Add comprehensive end-to-end tests:
   - Full CLI execution with test bundles
   - Integration with real FHIR Pseudonymizer container
   - Error scenarios and edge cases
   - Performance testing with large bundles

3. Implement production readiness features:
   - Proper logging configuration
   - Metrics and monitoring hooks
   - Graceful shutdown handling
   - Resource cleanup

4. Create comprehensive documentation:
   - Usage examples and tutorials  
   - Troubleshooting guide
   - Performance tuning recommendations

**Acceptance Criteria**:
- Complete CLI tool works end-to-end
- All tests pass including integration tests
- Performance meets requirements
- Documentation is complete and accurate

### Phase 6: Build and Deployment
**Goal**: Package and prepare for deployment

**Tasks**:
1. Configure Maven build:
   - Spring Boot executable JAR creation
   - Include all required dependencies
   - Set proper main class and manifest

2. Update project Makefile:
   - Add fhir-packager target
   - Support building standalone JAR
   - Optional Docker image creation

3. Add shell wrapper script (optional):
   - Simplify execution with proper Java opts
   - Handle common configuration scenarios
   - Environment variable setup

4. Create deployment documentation:
   - Installation instructions
   - System requirements
   - Configuration templates
   - Troubleshooting guides

**Acceptance Criteria**:
- Executable JAR builds correctly
- Makefile integration works
- Tool can be deployed and run on target systems
- Documentation supports easy deployment

## Technical Specifications

### API Integration Details

**FHIR Pseudonymizer Service**:
- Endpoint: `POST /fhir/$de-identify`
- Content-Type: `application/fhir+json`
- Accept: `application/fhir+json`
- Request Body: FHIR Bundle JSON
- Response Body: Pseudonymized FHIR Bundle JSON
- Authentication: Configurable (none, basic, bearer token)

### Configuration Schema

```yaml
pseudonymizer:
  url: "http://localhost:8080"
  timeout: "PT30S"
  retries: 3
  authentication:
    type: "none" # none, basic, bearer
    username: "${USERNAME:}"
    password: "${PASSWORD:}"
    token: "${TOKEN:}"

logging:
  level:
    care.smith.fts.packager: INFO
    root: WARN
```

### Error Codes

- **0**: Success
- **1**: General error (processing failure, network error)
- **2**: Invalid arguments or configuration
- **3**: Invalid input data (malformed FHIR Bundle)
- **4**: Service unavailable (pseudonymizer unreachable)

### Performance Requirements

- **Memory**: Should handle bundles up to 100MB with max 2GB heap
- **Processing**: < 1 second overhead per bundle (excluding network time)
- **Network**: Configurable timeouts (default 30s connect, 60s read)
- **Throughput**: Support for streaming large bundles without full memory load

### Testing Strategy

#### Unit Tests
- Configuration loading and validation
- CLI argument parsing
- FHIR Bundle processing logic
- Error handling and edge cases

#### Integration Tests  
- REST API client with WireMock
- End-to-end processing pipeline
- Configuration integration
- Error propagation

#### Performance Tests
- Large bundle processing
- Memory usage profiling
- Concurrent processing (if applicable)
- Network timeout handling

#### Container Tests
- Integration with real FHIR Pseudonymizer service
- Docker-based testing scenarios
- Network connectivity issues
- Service availability scenarios

## Security Considerations

- **Input Validation**: Validate FHIR Bundle structure before processing
- **Authentication**: Support secure communication with pseudonymizer service
- **Logging**: Never log patient data or sensitive information
- **Network**: Support HTTPS and certificate validation
- **Memory**: Prevent memory exhaustion with large inputs
- **Error Messages**: Avoid exposing internal system details in error messages

## Monitoring and Observability

- **Logging**: Structured logging with correlation IDs
- **Metrics**: Processing time, success/failure rates, bundle sizes
- **Health Checks**: Service availability monitoring
- **Error Tracking**: Detailed error categorization and reporting
- **Performance**: Memory usage and processing time tracking