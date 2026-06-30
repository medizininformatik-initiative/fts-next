package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.deidentification.configuration.VfpsDeIdentificationConfiguration;
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
class VfpsClientTest {

  @Mock private WebClient webClient;
  @Mock private RequestBodyUriSpec requestBodyUriSpec;
  @Mock private RequestBodySpec requestBodySpec;
  @Mock private RequestHeadersSpec requestHeadersSpec;
  @Mock private ResponseSpec responseSpec;

  private MeterRegistry meterRegistry;
  private VfpsDeIdentificationConfiguration config;
  private VfpsClient client;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    config = new VfpsDeIdentificationConfiguration();
    config.setConcurrency(4);
    client = new VfpsClient(webClient, meterRegistry, config);
  }

  @Test
  void fetchOrCreatePseudonymReturnsPseudonymValue() {
    var namespace = "test-namespace";
    var originalValue = "patient-123";
    var expectedPseudonym = "pseudo-abc";

    var response =
        new VfpsParameterResponse(
            "Parameters",
            java.util.List.of(
                new VfpsParameterResponse.Parameter("namespace", namespace, null),
                new VfpsParameterResponse.Parameter("originalValue", originalValue, null),
                new VfpsParameterResponse.Parameter("pseudonymValue", expectedPseudonym, null)));

    setupWebClientMock(response);

    var result = client.fetchOrCreatePseudonym(namespace, originalValue);

    StepVerifier.create(result).expectNext(expectedPseudonym).verifyComplete();
  }

  @Test
  void fetchOrCreatePseudonymsReturnsMappings() {
    var namespace = "test-namespace";
    var originals = Set.of("patient-1", "patient-2");

    // Use a single response that returns the same pseudonym pattern
    // The mapping uses originalValue from request, so we just need consistent pseudonym generation
    var response =
        new VfpsParameterResponse(
            "Parameters",
            java.util.List.of(
                new VfpsParameterResponse.Parameter("namespace", namespace, null),
                new VfpsParameterResponse.Parameter("originalValue", "any-value", null),
                new VfpsParameterResponse.Parameter("pseudonymValue", "pseudo-value", null)));

    setupWebClientMock(response);

    var result = client.fetchOrCreatePseudonyms(namespace, originals);

    StepVerifier.create(result)
        .assertNext(
            mappings -> {
              assertThat(mappings).hasSize(2);
              // Both entries map to the same pseudonym since we use a single mock response
              assertThat(mappings).containsKey("patient-1");
              assertThat(mappings).containsKey("patient-2");
              // Values should be the pseudonym from response
              assertThat(mappings.get("patient-1")).isEqualTo("pseudo-value");
              assertThat(mappings.get("patient-2")).isEqualTo("pseudo-value");
            })
        .verifyComplete();
  }

  @Test
  void fetchOrCreatePseudonymsReturnsEmptyMapForEmptyInput() {
    var result = client.fetchOrCreatePseudonyms("namespace", Set.of());

    StepVerifier.create(result)
        .assertNext(mappings -> assertThat(mappings).isEmpty())
        .verifyComplete();
  }

  @SuppressWarnings("unchecked")
  private void setupWebClientMock(VfpsParameterResponse response) {
    when(webClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(any(String.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
    when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(VfpsParameterResponse.class)).thenReturn(Mono.just(response));
  }
}
