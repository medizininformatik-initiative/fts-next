package care.smith.fts.util;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import java.util.Arrays;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "management.metrics.distribution")
@Data
public class MetricsConfig {

  @Bean
  public MeterRegistryCustomizer<MeterRegistry> ignoreUriQueryParamsCustomizer() {
    return registry -> registry.config().meterFilter(ignoreUriQueryParamsFilter());
  }

  private static MeterFilter ignoreUriQueryParamsFilter() {
    return new MeterFilter() {
      @Override
      public Meter.Id map(Meter.Id id) {
        if (id.getName().equals("http.client.requests")) {
          var uriTag = id.getTag("uri");
          if (uriTag != null && !uriTag.isBlank()) {
            var uri = Arrays.stream(uriTag.split("\\?")).findFirst().get();
            uri =
                uri.replaceAll(
                    "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}", "UUID");
            return id.withTag(Tag.of("uri", uri));
          }
        }
        return id;
      }
    };
  }
}
