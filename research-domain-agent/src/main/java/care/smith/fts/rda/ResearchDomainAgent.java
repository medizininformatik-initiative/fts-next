package care.smith.fts.rda;

import care.smith.fts.util.AgentConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@Import(AgentConfiguration.class)
public class ResearchDomainAgent {

  public static void main(String... args) {
    SpringApplication.run(ResearchDomainAgent.class, args);
  }

  @Bean
  public ObjectMapper transferProcessObjectMapper() {
    return new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());
  }

  @Bean
  public BulkheadRegistry bulkheadRegistry(
      TransferProcessRunnerConfig runnerConfig, MeterRegistry meterRegistry) {
    var bulkheadConfig =
        BulkheadConfig.custom()
            .maxConcurrentCalls(runnerConfig.maxConcurrentTransactions())
            .maxWaitDuration(Duration.ZERO)
            .build();
    var registry = BulkheadRegistry.of(bulkheadConfig);
    TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(meterRegistry);
    return registry;
  }
}
