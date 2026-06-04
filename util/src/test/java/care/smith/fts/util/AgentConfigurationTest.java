package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ReactorResourceFactory;

class AgentConfigurationTest {

  @Test
  void clientResourcesUseADedicatedConnectionProvider() {
    ReactorResourceFactory resources =
        new AgentConfiguration().ftsClientResources(Duration.ofSeconds(17));

    assertThat(resources).isNotNull();
    assertThat(resources.getConnectionProvider()).isNotNull();
    assertThat(resources.getConnectionProvider().name()).isEqualTo("fts-http-client");
  }
}
