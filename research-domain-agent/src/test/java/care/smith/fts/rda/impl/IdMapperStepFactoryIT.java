package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.impl.IdMapperStepConfig.TCAConfig;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class IdMapperStepFactoryIT {

  @Autowired MeterRegistry meterRegistry;
  private IdMapperStepFactory factory;

  @BeforeEach
  void setUp(@Autowired WebClientFactory clientFactory) {
    factory = new IdMapperStepFactory(clientFactory, meterRegistry);
  }

  @Test
  void getConfigType() {
    assertThat(factory.getConfigType()).isEqualTo(IdMapperStepConfig.class);
  }

  @Test
  void create() {
    assertThat(
            factory.create(
                new Deidentificator.Config(),
                new IdMapperStepConfig(new TCAConfig(new HttpClientConfig("baseUrl:1234")))))
        .isNotNull();
  }
}
