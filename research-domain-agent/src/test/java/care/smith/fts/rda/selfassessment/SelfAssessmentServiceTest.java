package care.smith.fts.rda.selfassessment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.TransferProcessConfig;
import care.smith.fts.rda.TransferProcessDefinition;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.selfassessment.Status;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class SelfAssessmentServiceTest {

  @RegisterExtension static WireMockExtension downstream = WireMockExtension.newInstance().build();

  private static WebClientFactory factoryReturning(String url) {
    var factory = mock(WebClientFactory.class);
    when(factory.create(any(HttpClientConfig.class)))
        .thenReturn(WebClient.builder().baseUrl(url).build());
    return factory;
  }

  private static TransferProcessDefinition def(String name, Map<String, ?> bundleSender) {
    return new TransferProcessDefinition(
        name,
        new TransferProcessConfig(Map.of(), bundleSender),
        mock(Deidentificator.class),
        mock(BundleSender.class));
  }

  @Test
  void allDownstreamsUp() {
    downstream.stubFor(get(urlPathMatching("/.*")).willReturn(aResponse().withStatus(200)));
    var url = downstream.getRuntimeInfo().getHttpBaseUrl();
    var svc =
        new SelfAssessmentService(
            List.of(def("p1", Map.of("fhirStore", Map.of("server", Map.of("baseUrl", url))))),
            factoryReturning(url),
            Duration.ofSeconds(5),
            4);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              assertThat(r.agent()).isEqualTo("research-domain-agent");
              assertThat(r.overall()).isEqualTo(Status.UP);
              assertThat(r.projects()).hasSize(1);
              assertThat(r.projects().get(0).downstream().get(0).status()).isEqualTo(Status.UP);
            })
        .verifyComplete();
  }

  @Test
  void downstreamDown_overallDown() {
    var deadUrl = "http://localhost:1";
    var svc =
        new SelfAssessmentService(
            List.of(def("p1", Map.of("fhirStore", Map.of("server", Map.of("baseUrl", deadUrl))))),
            factoryReturning(deadUrl),
            Duration.ofMillis(800),
            4);
    StepVerifier.create(svc.assess())
        .assertNext(r -> assertThat(r.overall()).isEqualTo(Status.DOWN))
        .verifyComplete();
  }

  @Test
  void projectWithoutDownstreams_projectUp() {
    var svc =
        new SelfAssessmentService(
            List.of(def("empty", Map.of())),
            mock(WebClientFactory.class),
            Duration.ofSeconds(5),
            4);
    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              assertThat(r.projects()).hasSize(1);
              assertThat(r.projects().get(0).downstream()).isEmpty();
              assertThat(r.projects().get(0).status()).isEqualTo(Status.UP);
            })
        .verifyComplete();
  }

  @Test
  void nullTimeoutFallsBackToDefault() {
    var svc =
        new SelfAssessmentService(
            List.of(def("empty", Map.of())), mock(WebClientFactory.class), null, 4);
    StepVerifier.create(svc.assess())
        .assertNext(r -> assertThat(r.overall()).isEqualTo(Status.UP))
        .verifyComplete();
  }
}
