package care.smith.fts.tca;

import static care.smith.fts.test.MockServerUtil.onRandomPort;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class BaseIT {
  // renovate: datasource=docker depName=valkey/valkey versioning=docker
  private static final String VALKEY_VERSION =
      "9.0.1-alpine@sha256:1be494495248d53e3558b198a1c704e6b559d5e99fe4c926e14a8ad24d76c6fa";

  private static final WireMockServer gics = onRandomPort();
  private static final WireMockServer gpas = onRandomPort();
  private static final RedisContainer keystore =
      new RedisContainer("valkey/valkey:" + VALKEY_VERSION);

  static {
    keystore.start();
  }

  protected WireMock gics() {
    return new WireMock(gics);
  }

  protected WireMock gpas() {
    return new WireMock(gpas);
  }

  @DynamicPropertySource
  static void registerGicsMockUrl(DynamicPropertyRegistry registry) {
    String baseUrl = "http://localhost:%d/ttp-fhir/fhir/gics".formatted(gics.port());
    registry.add("consent.gics.fhir.base-url", () -> baseUrl);
  }

  @DynamicPropertySource
  static void registerGpasMockUrl(DynamicPropertyRegistry registry) {
    String baseUrl = "http://localhost:%d/ttp-fhir/fhir/gpas".formatted(gpas.port());
    registry.add("de-identification.gpas.fhir.base-url", () -> baseUrl);
  }

  @DynamicPropertySource
  static void registerRedisMockUrl(DynamicPropertyRegistry registry) {
    String baseUrl = "redis://localhost:%d".formatted(keystore.getFirstMappedPort());
    registry.add("de-identification.keystore-url", () -> baseUrl);
  }
}
