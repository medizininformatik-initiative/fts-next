package care.smith.fts.tca.consent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

class GicsConfiguredTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ConditionalConfig.class);

  @Test
  void matchesWithGicsBaseUrl() {
    contextRunner
        .withPropertyValues("consent.gics.fhir.baseUrl=http://gics")
        .run(context -> assertThat(context).hasBean("marker"));
  }

  @Test
  void doesNotMatchWithoutGicsProperty() {
    contextRunner
        .withPropertyValues("consent.other.baseUrl=http://other")
        .run(context -> assertThat(context).doesNotHaveBean("marker"));
  }

  @Configuration
  @Conditional(GicsConfigured.class)
  static class ConditionalConfig {
    @Bean
    String marker() {
      return "marker";
    }
  }
}
