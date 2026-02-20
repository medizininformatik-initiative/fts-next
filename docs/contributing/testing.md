# Testing

FTSnext uses a multi-layer testing strategy to ensure correctness at different levels of
abstraction. This page explains the test types, how to run them, and the conventions to follow
when writing new tests.

## Test Types

| Type | Suffix | Location | Runs With |
|---|---|---|---|
| Unit | `*Test.java` | `src/test/java/` | `mvn test` |
| Integration | `*IT.java` | `src/test/java/` | `mvn verify` |
| Agent E2E | `*E2E.java` | `src/e2e/java/` | `mvn verify -Pe2e` |
| E2E | Shell scripts | `.github/test/` | `make` (Docker Compose) |

## Running Tests

### Unit Tests

```bash
# Run all unit tests
mvn clean test

# Run a specific test class
mvn test -Dtest=EverythingDataSelectorConfigTest

# Run a single test method
mvn test -Dtest=EverythingDataSelectorConfigTest#nullPageSizeUsesDefault
```

### Integration Tests

```bash
# Run all unit and integration tests
mvn clean verify

# Run tests for a specific agent
mvn clean verify --projects clinical-domain-agent --also-make
```

### Building Docker Images

Both agent E2E and E2E tests require locally built Docker images. Build them from the project
root with:

```bash
make
```

### Agent E2E Tests

Agent E2E tests run against real Docker containers using Testcontainers.

```bash
# Run all agent E2E tests for a specific agent
mvn clean verify -Pe2e --projects clinical-domain-agent --also-make

# Run a specific E2E test
mvn clean verify -Pe2e -Dit.test=TCACohortSelectorE2E \
  --projects clinical-domain-agent --also-make
```

### E2E Test

The full end-to-end test starts all agents with their external dependencies (Blaze FHIR servers,
gICS, gPAS, Keycloak, Redis) via Docker Compose.

```bash
cd .github/test
make generate-certs start upload-test-data
make transfer-all PROJECT=gics-consent-example
make wait
make check-status RESULTS_FILE=example.json
make check-resources check-pseudonymization
```

### Coverage

Code coverage is collected automatically in CI. The patch diff should be 100%.

```bash
# Generate an aggregate coverage report
mvn jacoco:report-aggregate@report
```

## Unit Tests

Unit tests verify isolated business logic without Spring context or external services.

### Conventions

- Suffix: `*Test.java`
- Use [AssertJ][assertj] for assertions
- Use Mockito for mocking dependencies
- Test classes are package-private (no `public` modifier)

### Example

```java
class EverythingDataSelectorConfigTest {

  private static final HttpClientConfig FHIR_SERVER =
      new HttpClientConfig("http://localhost");

  @Test
  void nullPageSizeUsesDefault() {
    assertThat(new EverythingDataSelectorConfig(FHIR_SERVER, null))
        .extracting(EverythingDataSelectorConfig::pageSize)
        .isEqualTo(DEFAULT_PAGE_SIZE);
  }
}
```

## Integration Tests

Integration tests verify components against mocked external services using [WireMock][wiremock]
within a Spring Boot context.

### Conventions

- Suffix: `*IT.java`
- Annotated with `@SpringBootTest` and `@WireMockTest`
- Use `MockServerUtil` for building WireMock responses
- Use `StepVerifier` from Reactor Test for reactive assertions

### Example

```java
@SpringBootTest
@WireMockTest
class TcaCohortSelectorIT {

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;
  private static TcaCohortSelector cohortSelector;
  private static MockCohortSelector allCohortSelector;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime,
             @Autowired WebClientFactory clientFactory) {
    var config = new TcaCohortSelectorConfig(/* ... */);
    cohortSelector = new TcaCohortSelector(
        config,
        clientFactory.create(clientConfig(wireMockRuntime)),
        meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
    allCohortSelector = MockCohortSelector.fetchAll(wireMock);
  }

  @Test
  void consentBundleSucceeds() {
    allCohortSelector.consentForOnePatient("patient");
    create(cohortSelector.selectCohort(List.of()))
        .expectNextCount(1)
        .verifyComplete();
  }
}
```

### Connection Scenario Tests

Extend `AbstractConnectionScenarioIT` as a `@Nested` class inside your integration test to
automatically run six resilience scenarios: connection reset, timeout, first request fails,
first and second fail, all fail, and wrong content type.

```java
@Nested
public class FetchAllRequest extends AbstractConnectionScenarioIT {
  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<ConsentedPatient>() {
      @Override
      public MappingBuilder requestBuilder() {
        return post("/api/v2/cd/consented-patients/fetch-all");
      }

      @Override
      public Flux<ConsentedPatient> executeStep() {
        return cohortSelector.selectCohort(List.of());
      }
    };
  }
}
```

### Authentication Tests

Extend `AbstractAuthIT` to verify that endpoints handle authentication correctly. The base class
provides six tests covering public/protected endpoints with correct, incorrect, and missing
credentials. OAuth2 tests require a running Keycloak instance. Start one with:

```bash
docker compose -f .github/test/oauth2/compose.yaml up --wait
```

```java
@Nested
@ActiveProfiles("auth_basic")
class BasicAuthIT extends AbstractAuthIT {
  @Override
  protected RequestHeadersSpec<?> protectedEndpoint(WebClient client) {
    return client.post().uri("/api/v2/cd/consented-patients/fetch-all");
  }
}
```

## Agent E2E Tests

Agent E2E tests run the actual agent inside a Docker container alongside WireMock containers for
its dependencies, connected via a Docker network.

### Conventions

- Suffix: `*E2E.java`
- Located in `src/e2e/java/`
- Activated via the Maven profile `-Pe2e`
- Extend abstract base classes per agent (e.g., `AbstractCohortSelectorE2E`)
- Use Testcontainers for container lifecycle management

### Example

```java
public class TCACohortSelectorE2E extends AbstractCohortSelectorE2E {

  public TCACohortSelectorE2E() {
    super("gics-consent-example.yaml");
  }

  @Override
  protected void setupSpecificTcaMocks() {
    var tcaWireMock = new WireMock(tca.getHost(), tca.getPort());
    var cohortGenerator = createCohortGenerator(
        "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym");
    var tcaResponse = new Bundle()
        .setEntry(List.of(new BundleEntryComponent()
            .setResource(cohortGenerator.generate())));

    tcaWireMock.register(
        post(urlPathMatching("/api/v2/cd/consented-patients.*"))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(fhirResponse(tcaResponse)));
  }

  @Test
  void testStartTransferAllProcessWithExampleProject() {
    executeTransferTest("[]");
  }
}
```

## Test Utilities

The `test-util` module provides shared testing infrastructure used across all agents. Key
components:

| Class | Purpose |
|---|---|
| `MockServerUtil` | WireMock response builders for FHIR and JSON responses, sequential mock scenarios |
| `FhirGenerators` | Template-based FHIR resource factory for generating test data |
| `FhirGenerator` | Reads JSON templates and replaces `$PLACEHOLDER` tokens with supplied values |
| `AbstractAuthIT` | Base class providing six authentication test scenarios |
| `AbstractConnectionScenarioIT` | Base class providing six connection resilience test scenarios |
| `TestWebClientFactory` | Spring test component providing pre-configured WebClient instances |

[assertj]: https://assertj.github.io/doc/

[wiremock]: https://wiremock.org/
