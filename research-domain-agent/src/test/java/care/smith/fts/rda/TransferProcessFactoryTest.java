package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.test.MockBundleSender;
import care.smith.fts.rda.test.MockDeidentificator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class TransferProcessFactoryTest {

  @Mock ApplicationContext appContext;
  @Spy MockDeidentificator deidentificatorFactory = new MockDeidentificator();
  @Spy MockBundleSender bundleSenderFactory = new MockBundleSender();

  TransferProcessFactory factory;

  @BeforeEach
  void setUp() {
    var mapper =
        new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    given(appContext.getBean("mockDeidentificator", Deidentificator.Factory.class))
        .willReturn(deidentificatorFactory);
    given(appContext.getBean("mockBundleSender", BundleSender.Factory.class))
        .willReturn(bundleSenderFactory);

    factory = new TransferProcessFactory(appContext, mapper);
  }

  @Test
  void validConfigYieldsNoErrors() {
    var processDefinition =
        new TransferProcessConfig(Map.of("mock", Map.of()), Map.of("mock", Map.of()));

    assertThat(factory.create(processDefinition, "example")).isNotNull();
  }

  @Test
  void implementationConfigIsGivenToImplementation() {
    var processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of()), Map.of("mock", Map.of("expect", Set.of("value-125591"))));

    assertThat(factory.create(processDefinition, "example")).isNotNull();

    var implConfig = new MockBundleSender.Config(Set.of("value-125591"));
    var commonConfig = new BundleSender.Config();
    verify(bundleSenderFactory).create(commonConfig, implConfig);
  }

  // commonConfig passing omitted, as no common config entries are exposed in RDA steps
}
