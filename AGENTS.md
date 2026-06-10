# AGENTS.md

This file provides guidance to coding agents when working with code in this repository.

## Build Commands
- **Build project**: `mvn clean install` or `make build`
- **Run tests**: `mvn clean verify` or `make test`
- **Run a single test**: `mvn test -Dtest=TestClassName#testMethodName`
- **Run a specific test class**: `mvn test -Dtest=TestClassName`
- **Run integration tests**: `mvn failsafe:integration-test`
- **Build coverage report**: `mvn jacoco:report-aggregate@report` or `make coverage`
- **Lint/style check**: `mvn checkstyle:check`
- **Build specific agent**: `make clinical-domain-agent` (or other agent name)
- **Build Docker images**: `make all` (builds all agents as containers)
- **Run specific agent module**: `mvn clean package -DskipTests --projects clinical-domain-agent --also-make`

## Architecture Overview

FTS-next is a multi-agent healthcare data transfer system implementing the SMITH architecture for secure FHIR data exchange between clinical and research domains.

### Core Architecture Pattern

The system follows a **three-agent architecture** with separation of concerns:

- **Clinical Domain Agent (CDA)**: Extracts and processes clinical data, applies initial deidentification
- **Trust Center Agent (TCA)**: Manages consent verification and pseudonymization services
- **Research Domain Agent (RDA)**: Receives and stores deidentified data in research systems

### Module Structure

- **api/**: Core interfaces and data models (`TransferProcessStep`, `ConsentedPatient`, `TransportBundle`)
- **util/**: Shared utilities (authentication, FHIR codecs, WebClient factories, metrics)
- **test-util/**: Testing infrastructure (`AbstractAuthIT`, `AbstractConnectionScenarioIT`, test data generators)
- **{agent}-agent/**: Agent-specific implementations following the same pattern:
  - Transfer process orchestration (`DefaultTransferProcessRunner`)
  - Step implementations (`impl/` package)
  - REST controllers (`rest/` package)
  - Configuration classes
- **trust-center-agent/adapters/**: Pluggable backends for pseudonymization (gPAS, Vfps, entici)

### Plugin Architecture

The system uses a factory pattern for pluggable processing steps:

```java
public interface TransferProcessStepFactory<STEPTYPE, CCONF, ICONF> {
  Class<ICONF> getConfigType();
  STEPTYPE create(CCONF commonConfig, ICONF implConfig);
}
```

Key step types:
- **CohortSelector**: Identifies patients based on consent (TcaCohortSelector, FhirCohortSelector)
- **DataSelector**: Extracts patient data (EverythingDataSelector)
- **Deidentificator**: Removes/transforms identifying information (DeidentifhirStep, FhirPseudonymizerStep)
- **BundleSender**: Transmits data between agents (RdaBundleSender, FhirStoreBundleSender)

### Configuration System

Transfer processes are defined in YAML project files under `projects/` directories:

```yaml
cohortSelector:
  tca:
    policies: ["research-policy"]
    domain: "clinical"
dataSelector:
  everything:
    maxPageSize: 1000
deidentificator:
  deidentifhir:
    profile: "research-profile"
bundleSender:
  rda:
    endpoint: "https://rda.example.com"
```

### Technology Stack

- **Java 21**
- **Spring Boot 4.0.x** with WebFlux (reactive programming)
- **HAPI FHIR 8.10.x** for FHIR R4 processing
- **Project Reactor** for non-blocking operations
- **Redisson 4.5.0** for reactive Redis operations (RMapCacheReactive for TTL-based session storage)
- **Maven** for build management
- **Docker** for containerization
- **JUnit 6** with AssertJ for testing

## Authentication & Security

The system supports multiple authentication methods configurable per agent:

- **None**: Development/testing
- **Basic Auth**: Username/password
- **OAuth2**: Client credentials flow
- **Client Certificates**: Mutual TLS

Authentication is configured via `HttpServerAuthConfig` with per-endpoint settings.

## Testing Strategy

- **Unit Tests**: Standard JUnit tests for business logic
- **Integration Tests**: Spring Boot tests with `@SpringBootTest`
- **Connection Scenario Tests**: Resilience testing using `AbstractConnectionScenarioIT`
- **End-to-End Tests**: Full workflow testing across agents (`.github/test/`)

For integration tests, extend `AbstractAuthIT` to ensure proper authentication handling.

**Note**: `OAuth2AuthIT` test errors are expected in local development environments without proper OAuth2 infrastructure.

## Code Style Guidelines
- Follow Google Java Style Guide (enforced by checkstyle with google_checks.xml)
- Use standard Java imports ordering (no wildcards)
- Prefer static imports for utility methods (e.g., `import static java.util.Objects.requireNonNull;`)
- Fail fast on null: use `@NotNull` annotations + `requireNonNull()` at boundaries, don't defensively check null throughout
- Avoid unnecessary comments: code should be self-explanatory; only comment non-obvious "why", not "what"
- Use lombok annotations for boilerplate reduction (e.g., @Slf4j, @ToString), but avoid `@UtilityClass` — use interfaces with static methods instead
- Follow standard Java naming conventions (camelCase for methods/variables, PascalCase for classes)
- Use records for immutable data classes where appropriate
- Proper exception handling with descriptive messages
- Use Optional for values that might be missing
- Always include unit tests for new functionality
- Use assertj for fluent test assertions

### Functional Programming Principles
- Favor immutability and pure functions over stateful code
- Use Java `record` types for immutable data structures
- Avoid side effects; isolate and document when necessary
- Prefer composition over inheritance
- Use reactive streams (Mono/Flux) instead of imperative loops
- Prefer `Optional` chains over imperative null checks (e.g., `Optional.ofNullable(x).map(...).filter(...).ifPresent(...)`)
- Files should not exceed 300 LOC (soft limit)

## Development Workflow

### Test-Driven Development (TDD) - REQUIRED
For any feature or bugfix, write a test before writing implementation code. Implement vertical slices via tracer bullets: one test → one minimal implementation → repeat. NEVER write all tests first, then all code (horizontal slicing produces tests coupled to imagined behavior).

1. State how you will verify the change (test, bash command, browser check).
2. Write ONE test for ONE behavior first — it must fail (RED).
3. Write the minimal code to pass (GREEN), then iterate to the next behavior.
4. Refactor only while green. Tests verify behavior through public interfaces, never implementation details.
 
We require 100% patch coverage. 

## Additional Notes

- **ALWAYS run `make format` before committing**
- **Agent E2E tests must pass before committing**: Run all with `mvn clean verify -Pe2e --also-make`, or a specific test with `mvn clean verify -Pe2e -Dit.test=TCACohortSelectorE2E --projects clinical-domain-agent --also-make`
- **Always use `mvn verify` instead of `mvn test`** - verify runs integration tests (failsafe) in addition to unit tests (surefire)
- Prefer Slf4j logging over System.out.println
- Use existing retry strategies (`defaultRetryStrategy`) for external service calls
- Follow existing patterns (e.g., DeidentifhirStep) when implementing new transfer process steps
- Use Title Case For Git Commit Messages
- Commit Title MUST NOT exceed 50 chars
- Commit Description MUST NOT exceed 80 chars
- Branch naming format: `issuenumber-title` (e.g., `1396-dateshift-id-pattern`)
- **Epic branches** (e.g., `epic/fhir-pseudonymizer`) are long-lived integration branches; feature branches merge into the epic, not directly into `main`
- **PR descriptions**: Do NOT include a "Test plan" section

### FHIR Server Tooling
- **Blaze FHIR servers**: Use `blazectl` for interacting with Blaze FHIR servers (uploading bundles, counting resources, etc.) instead of raw curl/HTTP commands

### Critical Constraints
- **NO `.block()` calls in production code** - Use reactive patterns (Mono/Flux) throughout
- **Never log PHI** (Protected Health Information) - Only log anonymized identifiers
- **Data isolation**: Clinical data must never flow through TCA - only identifiers
- **Security by design**: Perform threat modeling for any feature handling patient data
- **Input validation**: Validate all external input at system boundaries (especially FHIR resources)

### Error Handling
- **Stop Hook Errors**: If you encounter a stop hook error, attempt to resolve it rather than stopping immediately. Analyze the error, understand what caused it, and try to fix the underlying issue.
