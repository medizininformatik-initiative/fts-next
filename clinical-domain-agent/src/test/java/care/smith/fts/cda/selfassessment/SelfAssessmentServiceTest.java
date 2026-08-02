package care.smith.fts.cda.selfassessment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.TransferProcessConfig;
import care.smith.fts.cda.TransferProcessDefinition;
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
        new TransferProcessConfig(Map.of(), Map.of(), Map.of(), bundleSender),
        mock(CohortSelector.class),
        mock(DataSelector.class),
        mock(Deidentificator.class),
        mock(BundleSender.class));
  }

  @Test
  void singleProject_allDownstreamsUp() {
    downstream.stubFor(get(urlPathMatching("/.*")).willReturn(aResponse().withStatus(200)));
    var url = downstream.getRuntimeInfo().getHttpBaseUrl();
    var svc =
        new SelfAssessmentService(
            List.of(def("p1", Map.of("rda", Map.of("server", Map.of("baseUrl", url))))),
            factoryReturning(url),
            Duration.ofSeconds(5),
            4);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              assertThat(r.agent()).isEqualTo("clinical-domain-agent");
              assertThat(r.overall()).isEqualTo(Status.UP);
              assertThat(r.projects()).hasSize(1);
              assertThat(r.projects().get(0).downstream()).hasSize(1);
              assertThat(r.projects().get(0).downstream().get(0).status()).isEqualTo(Status.UP);
            })
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
  void downstreamConnectionRefused_projectDown() {
    var deadUrl = "http://localhost:1";
    var svc =
        new SelfAssessmentService(
            List.of(def("p1", Map.of("rda", Map.of("server", Map.of("baseUrl", deadUrl))))),
            factoryReturning(deadUrl),
            Duration.ofMillis(800),
            4);
    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              assertThat(r.overall()).isEqualTo(Status.DOWN);
              assertThat(r.projects().get(0).status()).isEqualTo(Status.DOWN);
            })
        .verifyComplete();
  }
}
