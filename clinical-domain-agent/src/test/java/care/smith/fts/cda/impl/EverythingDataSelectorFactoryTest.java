package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import care.smith.fts.cda.services.FhirResolveConfig;
import care.smith.fts.util.HTTPClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class EverythingDataSelectorFactoryTest {

  @Autowired WebClient.Builder client;

  @Test
  void testConfigType() {
    assertThat(new EverythingDataSelectorFactory(client).getConfigType()).isNotNull();
  }

  @Test
  void testCreateWithoutResolver() {
    assertThat(
            new EverythingDataSelectorFactory(client)
                .create(
                    null,
                    new EverythingDataSelectorConfig(new HTTPClientConfig("http://localhost"))))
        .isNotNull();
  }

  @Test
  void testCreateWithResolver() {
    assertThat(
            new EverythingDataSelectorFactory(client)
                .create(
                    null,
                    new EverythingDataSelectorConfig(
                        new HTTPClientConfig("http://localhost"),
                        new FhirResolveConfig("https://patient-identifier.example.com"))))
        .isNotNull();
  }
}
