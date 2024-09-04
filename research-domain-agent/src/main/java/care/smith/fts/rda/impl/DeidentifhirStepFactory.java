package care.smith.fts.rda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.rda.Deidentificator;
import com.typesafe.config.ConfigFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("deidentifhirDeidentificator")
public class DeidentifhirStepFactory implements Deidentificator.Factory<DeidentifhirStepConfig> {

  private final WebClient.Builder builder;
  private final MeterRegistry meterRegistry;

  public DeidentifhirStepFactory(
      WebClient.Builder builder, @Autowired MeterRegistry meterRegistry) {
    this.builder = builder;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<DeidentifhirStepConfig> getConfigType() {
    return DeidentifhirStepConfig.class;
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, DeidentifhirStepConfig implConfig) {

    var httpClient = implConfig.tca().server().createClient(builder);
    var config = ConfigFactory.parseFile(requireNonNull(implConfig.deidentifhirConfig()));
    return new DeidentifhirStep(
        config, httpClient, implConfig.tca().domain(), implConfig.dateShift(), meterRegistry);
  }
}
