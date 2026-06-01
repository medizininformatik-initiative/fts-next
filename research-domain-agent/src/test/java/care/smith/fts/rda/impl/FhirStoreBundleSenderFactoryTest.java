package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class FhirStoreBundleSenderFactoryTest {

  @Mock WebClientFactory clientFactory;
  @Mock WebClient webClient;

  @Test
  void getConfigTypeReturnsImplConfig() {
    var factory =
        new FhirStoreBundleSenderFactory(
            clientFactory, new DefaultRetryStrategy(new SimpleMeterRegistry()));
    assertThat(factory.getConfigType()).isEqualTo(FhirStoreBundleSenderConfig.class);
  }

  @Test
  void createBuildsSenderFromConfig() {
    var server = new HttpClientConfig("http://blaze:8080/fhir");
    var implConfig = new FhirStoreBundleSenderConfig(server, "project", 4);
    given(clientFactory.create(server)).willReturn(webClient);

    var factory =
        new FhirStoreBundleSenderFactory(
            clientFactory, new DefaultRetryStrategy(new SimpleMeterRegistry()));
    BundleSender sender = factory.create(new BundleSender.Config(), implConfig);

    assertThat(sender).isNotNull();
    assertThat(sender.destinationId()).isEqualTo("http://blaze:8080/fhir");
    assertThat(sender.sendConcurrency()).isEqualTo(4);
    verify(clientFactory).create(server);
  }
}
