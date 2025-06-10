package care.smith.fts.cda.e2e;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.wiremock.integrations.testcontainers.WireMockContainer;

public class ExternalCohortSelectorE2E {
  Network network = Network.newNetwork();

  String buildNr;

  GenericContainer<?> cda =
      new GenericContainer<>(
              "ghcr.io/medizininformatik-initiative/fts/clinical-domain-agent:" + buildNr)
          .withFileSystemBind("", "/app/projects/project.yaml")
          .withNetwork(network);

  WireMockContainer cdHds =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("cd-hds");
  WireMockContainer tca =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("tc-agent");
  WireMockContainer rda =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("rd-agent");

  @Test
  void someTest() {
    System.out.println("Success");
  }
}
