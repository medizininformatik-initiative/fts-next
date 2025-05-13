package care.smith.fts.tca.consent;

import java.util.Arrays;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** A Spring condition that checks if consent via gICS is configured. */
@Slf4j
public class GicsConfigured implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var env = (ConfigurableEnvironment) context.getEnvironment();
    var propertySources = env.getPropertySources();

    boolean hasGicsProperties =
        StreamSupport.stream(propertySources.spliterator(), false)
            .filter(EnumerablePropertySource.class::isInstance)
            .map(EnumerablePropertySource.class::cast)
            .flatMap(ps -> Arrays.stream(ps.getPropertyNames()))
            .anyMatch(prop -> prop.startsWith("consent.gics."));

    log.debug("Has consent.gics.* properties: {}", hasGicsProperties);
    return hasGicsProperties;
  }
}
