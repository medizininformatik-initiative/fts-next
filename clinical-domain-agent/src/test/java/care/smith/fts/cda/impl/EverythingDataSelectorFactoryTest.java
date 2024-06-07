package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import care.smith.fts.cda.services.FhirResolveConfig;
import care.smith.fts.util.HTTPClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EverythingDataSelectorFactoryTest {

  @Autowired FhirContext fhir;

  @Test
  void testConfigType() {
    IRestfulClientFactory clientFactory = fhir.getRestfulClientFactory();
    assertThat(new EverythingDataSelectorFactory(clientFactory, fhir).getConfigType()).isNotNull();
  }

  @Test
  void testCreateWithoutResolver() {
    assertThat(
            new EverythingDataSelectorFactory(fhir.getRestfulClientFactory(), fhir)
                .create(
                    null,
                    new EverythingDataSelectorConfig(
                        new HTTPClientConfig("http://localhost"))))
        .isNotNull();
  }

  @Test
  void testCreateWithResolver() {
    assertThat(
            new EverythingDataSelectorFactory(fhir.getRestfulClientFactory(), fhir)
                .create(
                    null,
                    new EverythingDataSelectorConfig(
                        new HTTPClientConfig("http://localhost"),
                        new FhirResolveConfig("https://patient-identifier.example.com"))))
        .isNotNull();
  }
}
