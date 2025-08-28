package care.smith.fts.packager.config;

import care.smith.fts.packager.service.PseudonymizerClient;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * Test configuration that provides mock implementations for integration tests.
 */
@TestConfiguration
public class MockPseudonymizerTestConfiguration {

  /**
   * Mock PseudonymizerClient that returns the input bundle unchanged.
   * This allows integration tests to run without needing a real pseudonymizer service.
   */
  @Bean
  @Primary
  public PseudonymizerClient mockPseudonymizerClient() {
    return new PseudonymizerClient() {
      @Override
      public Mono<Bundle> pseudonymize(Bundle bundle) {
        // Return the input bundle unchanged (identity transform for testing)
        return Mono.just(bundle);
      }

      @Override
      public Mono<Bundle> pseudonymize(Bundle bundle, org.hl7.fhir.r4.model.Parameters customConfig) {
        // Return the input bundle unchanged (identity transform for testing)
        return Mono.just(bundle);
      }

      @Override
      public Mono<HealthStatus> checkHealth() {
        // Always return healthy for testing
        return Mono.just(HealthStatus.healthy(10));
      }
    };
  }
}