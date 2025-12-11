package care.smith.fts.tca.deidentification;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.util.Set.of;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.AbstractFhirClientIT;
import care.smith.fts.tca.deidentification.configuration.VfpsDeIdentificationConfiguration;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.Set;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest
public class VfpsClientIT extends AbstractFhirClientIT<VfpsClient, String, Map<String, String>> {

  @Autowired WebClient.Builder httpClientBuilder;

  @Autowired MeterRegistry meterRegistry;

  @MockitoBean RedissonClient redisClient; // Mock redisClient to allow tests to start

  @BeforeEach
  void setUpDependencies() {
    init(httpClientBuilder, meterRegistry);
  }

  private static final String REQUEST_BODY =
      """
          {
            "resourceType": "Parameters",
            "parameter": [
              {"name": "namespace", "valueString": "domain"},
              {"name": "originalValue", "valueString": "id"}
            ]
          }
      """;

  @Override
  protected VfpsClient createClient(String baseUrl) {
    var config = new VfpsDeIdentificationConfiguration();
    return new VfpsClient(httpClientBuilder.baseUrl(baseUrl).build(), meterRegistry, config);
  }

  @Override
  protected MappingBuilder getRequestMappingBuilder() {
    return post(urlPathEqualTo("/$create-pseudonym")).withRequestBody(equalToJson(REQUEST_BODY));
  }

  @Override
  protected CapabilityStatement getMockCapabilityStatement() {
    var capabilities = new CapabilityStatement();
    var rest = capabilities.addRest();
    rest.addOperation().setName("create-pseudonym");
    return capabilities;
  }

  @Override
  protected Mono<Map<String, String>> executeRequest(String request) {
    String[] parts = request.split(":");
    return client.fetchOrCreatePseudonyms(parts[0], of(parts[1]));
  }

  @Override
  protected String getServerName() {
    return "Vfps";
  }

  @Override
  protected String getDefaultRequest() {
    return "domain:id";
  }

  @Override
  protected Mono<Map<String, String>> executeRequestWithClient(
      VfpsClient specificClient, String request) {
    String[] parts = request.split(":");
    return specificClient.fetchOrCreatePseudonyms(parts[0], of(parts[1]));
  }

  @Test
  void fetchOrCreatePseudonymsReturnsEmptyMapForEmptyInput() {
    create(client.fetchOrCreatePseudonyms("domain", Set.of()))
        .assertNext(result -> assertThat(result).isEmpty())
        .verifyComplete();
  }
}
