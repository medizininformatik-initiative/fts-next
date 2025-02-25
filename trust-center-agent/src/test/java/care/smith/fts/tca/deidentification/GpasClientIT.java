package care.smith.fts.tca.deidentification;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import care.smith.fts.tca.AbstractFhirClientIT;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import io.micrometer.core.instrument.MeterRegistry;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@SpringBootTest
public class GpasClientIT extends AbstractFhirClientIT<GpasClient, String, String> {

  @Autowired WebClient.Builder httpClientBuilder;

  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  @BeforeEach
  void setUpDependencies() {
    init(httpClientBuilder, meterRegistry);
  }

  private static final String REQUEST_BODY =
      """
          {
            "resourceType": "Parameters",
            "parameter": [
              {"name": "target", "valueString": "domain"},
              {"name": "original", "valueString": "id"}
              ]
           }
      """;

  @Override
  protected GpasClient createClient(String baseUrl) {
    return new GpasClient(httpClientBuilder.baseUrl(baseUrl).build(), meterRegistry);
  }

  @Override
  protected MappingBuilder getRequestMappingBuilder() {
    return post(urlPathEqualTo("/$pseudonymizeAllowCreate"))
        .withRequestBody(equalToJson(REQUEST_BODY));
  }

  @Override
  protected CapabilityStatement getMockCapabilityStatement() {
    var capabilities = new CapabilityStatement();
    var rest = capabilities.addRest();
    rest.addOperation().setName("pseudonymizeAllowCreate");
    return capabilities;
  }

  @Override
  protected Mono<String> executeRequest(String request) {
    String[] parts = request.split(":");
    return client.fetchOrCreatePseudonym(parts[0], parts[1]);
  }

  @Override
  protected String getServerName() {
    return "gPAS";
  }

  @Override
  protected String getDefaultRequest() {
    return "domain:id";
  }

  @Override
  protected Mono<String> executeRequestWithClient(GpasClient specificClient, String request) {
    String[] parts = request.split(":");
    return specificClient.fetchOrCreatePseudonym(parts[0], parts[1]);
  }
}
