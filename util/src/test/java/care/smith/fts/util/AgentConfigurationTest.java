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

  @Test
  void clientResourcesCapConnectionsToTheOutboundFanout() {
    ReactorResourceFactory resources =
        new AgentConfiguration().ftsClientResources(Duration.ofSeconds(25));

    // The per-host cap must equal the pipeline's fan-out (one constant, shared with the
    // select/deidentify flatMaps) so the pool never throttles a transfer below its dispatch rate.
    // Without the explicit cap the pool would fall back to reactor-netty's library default
    // (max(cores,8)*2, >=16) and reject or stall high-fan-out bursts.
    assertThat(resources.getConnectionProvider().maxConnections())
        .isEqualTo(AgentConfiguration.MAX_OUTBOUND_FANOUT);
  }
}
