package care.smith.fts.util.auth;

import java.util.Arrays;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OAuth2ConfigurationExistsCondition implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    ConfigurableEnvironment env = (ConfigurableEnvironment) context.getEnvironment();
    return env.getPropertySources().stream()
        .filter(ps -> ps instanceof EnumerablePropertySource)
        .map(ps -> (EnumerablePropertySource<?>) ps)
        .flatMap(ps -> Arrays.stream(ps.getPropertyNames()))
        .anyMatch(prop -> prop.startsWith("spring.security.oauth2"));
  }
}
