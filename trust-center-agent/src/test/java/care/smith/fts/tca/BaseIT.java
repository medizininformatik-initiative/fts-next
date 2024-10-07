package care.smith.fts.tca;

import static care.smith.fts.test.MockServerUtil.onRandomPort;

import com.redis.testcontainers.RedisContainer;
import org.mockserver.client.MockServerClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public class BaseIT {
  // renovate: datasource=github-releases depName=valkey-io/valkey
  private static final String VALKEY_VERSION = "8.0.1";

  protected static final MockServerClient gics = onRandomPort();
  protected static final MockServerClient gpas = onRandomPort();
  protected static final RedisContainer keystore =
      new RedisContainer("valkey/valkey:" + VALKEY_VERSION + "-alpine");

  static {
    keystore.start();
  }

  @DynamicPropertySource
  static void registerGicsMockUrl(DynamicPropertyRegistry registry) {
    String baseUrl = "http://localhost:%d/ttp-fhir/fhir/gics".formatted(gics.getPort());
    registry.add("consent.gics.fhir.base-url", () -> baseUrl);
  }

  @DynamicPropertySource
  static void registerGpasMockUrl(DynamicPropertyRegistry registry) {
    String baseUrl = "http://localhost:%d/ttp-fhir/fhir/gpas".formatted(gpas.getPort());
    registry.add("de-identification.gpas.fhir.base-url", () -> baseUrl);
  }

  @DynamicPropertySource
  static void registerRedisMockUrl(DynamicPropertyRegistry registry) {
    String baseUrl = "redis://localhost:%d".formatted(keystore.getFirstMappedPort());
    registry.add("de-identification.keystore-url", () -> baseUrl);
  }
}
