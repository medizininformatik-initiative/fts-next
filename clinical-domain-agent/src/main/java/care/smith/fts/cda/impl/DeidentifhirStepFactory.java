package care.smith.fts.cda.impl;

import static com.typesafe.config.ConfigFactory.parseFile;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.cda.Deidentificator;
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

    return new DeidentifhirStep(
        httpClient,
        implConfig.tca().domain(),
        implConfig.dateShift(),
        parseFile(requireNonNull(implConfig.deidentifhirConfig())),
        parseFile(requireNonNull(implConfig.scraperConfig())),
        meterRegistry);
  }
}
