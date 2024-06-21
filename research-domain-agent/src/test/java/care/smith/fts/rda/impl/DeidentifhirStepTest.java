package care.smith.fts.rda.impl;

import static com.typesafe.config.ConfigFactory.parseResources;
import static java.time.Duration.ofDays;

import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.test.MockServerUtil;
import com.typesafe.config.Config;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockServerExtension.class)
class DeidentifhirStepTest {
  private DeidentifhirStep step;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    Config config = parseResources(DeidentifhirUtil.class, "TransportToRd.profile");
    var server = MockServerUtil.clientConfig(mockServer);

    step =
        new DeidentifhirStep(
            config, server.createClient(WebClient.builder()), "domain", ofDays(14));
  }

  @Test
  void replaceIDs() {}
}
