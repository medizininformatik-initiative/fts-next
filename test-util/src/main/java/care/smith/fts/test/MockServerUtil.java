package care.smith.fts.test;

import static care.smith.fts.test.MockServerUtil.SequentialMock.newScenario;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static care.smith.fts.util.NanoIdUtils.nanoId;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static java.lang.Math.toIntExact;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import care.smith.fts.util.HttpClientConfig;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Resource;

public interface MockServerUtil {

  static HttpClientConfig clientConfig(WireMockRuntimeInfo server) {
    var address = server.getHttpBaseUrl();
    return new HttpClientConfig(address);
  }

  static InputStream getResourceAsStream(String resourceName) {
    return MockServerUtil.class.getResourceAsStream(resourceName);
  }

  static WireMockServer onRandomPort() {
    var wireMockServer = new WireMockServer(options().dynamicPort());
    wireMockServer.start();
    return wireMockServer;
  }

  String APPLICATION_FHIR_JSON = "application/fhir+json";

  String FIRST = Scenario.STARTED;
  String REST = "OtherRequests";

  static SequentialMock sequentialMock(WireMock wireMock) {
    return new InitialSequentialMock(wireMock);
  }

  static ResponseDefinitionBuilder accepted() {
    return status(202);
  }

  interface SequentialMock {
    SequentialMock then(MappingBuilder mapping, ResponseDefinitionBuilder response);

    void thereafter(MappingBuilder mapping, ResponseDefinitionBuilder response);

    static String newScenario() {
      return "seq/%s".formatted(nanoId(4));
    }
  }

  class InitialSequentialMock implements SequentialMock {
    private final WireMock wireMock;

    public InitialSequentialMock(WireMock wireMock) {
      this.wireMock = wireMock;
    }

    @Override
    public SequentialMock then(MappingBuilder mapping, ResponseDefinitionBuilder response) {
      return new ChainedSequentialMock(this, wireMock, mapping, response);
    }

    @Override
    public void thereafter(MappingBuilder mapping, ResponseDefinitionBuilder response) {
      var scenario = mapping.inScenario(newScenario());
      wireMock.register(scenario.whenScenarioStateIs(FIRST).willReturn(response));
    }
  }

  @Slf4j
  class ChainedSequentialMock implements SequentialMock {
    private final SequentialMock previous;
    private final WireMock wireMock;
    private final MappingBuilder mapping;
    private final ResponseDefinitionBuilder response;

    public ChainedSequentialMock(
        SequentialMock previous,
        WireMock wireMock,
        MappingBuilder mapping,
        ResponseDefinitionBuilder response) {
      this.previous = previous;
      this.wireMock = wireMock;
      this.mapping = mapping;
      this.response = response;
    }

    @Override
    public SequentialMock then(MappingBuilder mapping, ResponseDefinitionBuilder response) {
      return new ChainedSequentialMock(this, wireMock, mapping, response);
    }

    @Override
    public void thereafter(MappingBuilder mapping, ResponseDefinitionBuilder response) {
      String scenarioName = newScenario();
      wireMock.register(
          mapping
              .withId(UUID.randomUUID())
              .inScenario(scenarioName)
              .whenScenarioStateIs(REST)
              .willReturn(response));
      build(scenarioName, REST);
    }

    private void build(String scenarioName, String nextState) {
      var stateName = hasPrevious() ? nanoId(8) : FIRST;
      log.debug("Registering {}[{} -> {}]", scenarioName, stateName, nextState);
      wireMock.register(
          mapping
              .withId(UUID.randomUUID())
              .inScenario(scenarioName)
              .whenScenarioStateIs(stateName)
              .willSetStateTo(nextState)
              .willReturn(response));
      if (hasPrevious()) {
        ((ChainedSequentialMock) previous).build(scenarioName, stateName);
      }
    }

    private boolean hasPrevious() {
      return previous instanceof ChainedSequentialMock;
    }
  }

  static ResponseDefinitionBuilder fhirResponse(Resource bundle, int statusCode) {
    return fhirResponse(fhirResourceToString(bundle), statusCode);
  }

  static ResponseDefinitionBuilder fhirResponse(String body, int statusCode) {
    return status(statusCode).withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON_VALUE).withBody(body);
  }

  static ResponseDefinitionBuilder connectionReset() {
    return aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER);
  }

  static ResponseDefinitionBuilder delayedResponse() {
    return delayedResponse(Duration.ofMinutes(10));
  }

  static ResponseDefinitionBuilder delayedResponse(Duration d) {
    return noContent().withFixedDelay(toIntExact(d.toMillis()));
  }
}
