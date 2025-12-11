package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.deidentification.configuration.EnticiDeIdentificationConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestBodySpec;
import org.springframework.web.reactive.function.client.WebClient.RequestBodyUriSpec;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class EnticiClientTest {

  @Mock private WebClient webClient;
  @Mock private RequestBodyUriSpec requestBodyUriSpec;
  @Mock private RequestBodySpec requestBodySpec;
  @Mock private RequestHeadersSpec requestHeadersSpec;
  @Mock private ResponseSpec responseSpec;

  private MeterRegistry meterRegistry;
  private EnticiDeIdentificationConfiguration config;
  private EnticiClient client;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    config = new EnticiDeIdentificationConfiguration();
    config.setConcurrency(4);
    config.setResourceType("Patient");
    client = new EnticiClient(webClient, meterRegistry, config);
  }

  @Test
  void fetchOrCreatePseudonymReturnsPseudonymValue() {
    var domain = "http://test-domain";
    var originalValue = "patient-123";
    var expectedPseudonym = "pseudo-abc";

    var response =
        new EnticiParameterResponse(
            "Parameters",
            java.util.List.of(
                new EnticiParameterResponse.Parameter(
                    "pseudonym",
                    null,
                    new EnticiParameterResponse.ValueIdentifier(domain, expectedPseudonym))));

    setupWebClientMock(response);

    var result = client.fetchOrCreatePseudonym(domain, originalValue);

    StepVerifier.create(result).expectNext(expectedPseudonym).verifyComplete();
  }

  @Test
  void fetchOrCreatePseudonymsReturnsMappings() {
    var domain = "http://test-domain";
    var originals = Set.of("patient-1", "patient-2");

    var response =
        new EnticiParameterResponse(
            "Parameters",
            java.util.List.of(
                new EnticiParameterResponse.Parameter(
                    "pseudonym",
                    null,
                    new EnticiParameterResponse.ValueIdentifier(domain, "pseudo-value"))));

    setupWebClientMock(response);

    var result = client.fetchOrCreatePseudonyms(domain, originals);

    StepVerifier.create(result)
        .assertNext(
            mappings -> {
              assertThat(mappings).hasSize(2);
              assertThat(mappings).containsKey("patient-1");
              assertThat(mappings).containsKey("patient-2");
              assertThat(mappings.get("patient-1")).isEqualTo("pseudo-value");
              assertThat(mappings.get("patient-2")).isEqualTo("pseudo-value");
            })
        .verifyComplete();
  }

  @Test
  void fetchOrCreatePseudonymsReturnsEmptyMapForEmptyInput() {
    var result = client.fetchOrCreatePseudonyms("domain", Set.of());

    StepVerifier.create(result)
        .assertNext(mappings -> assertThat(mappings).isEmpty())
        .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  private void setupWebClientMock(EnticiParameterResponse response) {
    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
    when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(EnticiParameterResponse.class)).thenReturn(Mono.just(response));
  }
}
