package care.smith.fts.tca.consent;

import java.util.Arrays;
import java.util.stream.StreamSupport;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** A Spring condition that checks if consent via gICS is configured. */
public class GicsConfigured implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var env = (ConfigurableEnvironment) context.getEnvironment();
    var propertySources = env.getPropertySources();

    return StreamSupport.stream(propertySources.spliterator(), false)
        .filter(EnumerablePropertySource.class::isInstance)
        .map(EnumerablePropertySource.class::cast)
        .flatMap(ps -> Arrays.stream(ps.getPropertyNames()))
        .anyMatch(prop -> prop.startsWith("consent.gics."));
  }
}
