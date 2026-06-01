package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockitoExtension.class)
class RdaBundleSenderFactoryTest {

  @Mock WebClientFactory clientFactory;
  @Mock WebClient webClient;

  @Test
  void getConfigTypeReturnsImplConfig() {
    var factory = new RdaBundleSenderFactory(clientFactory, new SimpleMeterRegistry());
    assertThat(factory.getConfigType()).isEqualTo(RdaBundleSenderConfig.class);
  }

  @Test
  void createBuildsSenderFromConfig() {
    var server = new HttpClientConfig("http://rda:8080");
    var implConfig = new RdaBundleSenderConfig(server, "project", Duration.ofMinutes(5));
    given(clientFactory.create(server)).willReturn(webClient);

    var factory = new RdaBundleSenderFactory(clientFactory, new SimpleMeterRegistry());
    BundleSender sender = factory.create(new BundleSender.Config(), implConfig);

    assertThat(sender).isNotNull();
    verify(clientFactory).create(server);
  }
}
