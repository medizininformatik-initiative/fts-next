package care.smith.fts.util.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;

class OAuth2ConfigurationExistsConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ConditionalConfig.class);

  @Test
  void matchesWithOAuth2Property() {
    contextRunner
        .withPropertyValues("spring.security.oauth2.client.registration.x.client-id=id")
        .run(context -> assertThat(context).hasBean("marker"));
  }

  @Test
  void doesNotMatchWithoutOAuth2Property() {
    contextRunner
        .withPropertyValues("spring.security.user.name=user")
        .run(context -> assertThat(context).doesNotHaveBean("marker"));
  }

  @Test
  void ignoresNonEnumerablePropertySource() {
    contextRunner
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(new NonEnumerablePropertySource("spring.security.oauth2.x")))
        .run(context -> assertThat(context).doesNotHaveBean("marker"));
  }

  private static class NonEnumerablePropertySource extends PropertySource<Object> {
    private final String name;

    NonEnumerablePropertySource(String name) {
      super("non-enumerable");
      this.name = name;
    }

    @Override
    public Object getProperty(String property) {
      return name.equals(property) ? "value" : null;
    }
  }

  @Configuration
  @Conditional(OAuth2ConfigurationExistsCondition.class)
  static class ConditionalConfig {
    @Bean
    String marker() {
      return "marker";
    }
  }
}
