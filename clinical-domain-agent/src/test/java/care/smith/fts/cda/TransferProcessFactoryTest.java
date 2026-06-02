package care.smith.fts.cda;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.test.MockBundleSender;
import care.smith.fts.cda.test.MockCohortSelector;
import care.smith.fts.cda.test.MockDataSelector;
import care.smith.fts.cda.test.MockDeidentificator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
  @Spy MockCohortSelector cohortSelectorFactory = new MockCohortSelector();
  @Spy MockDataSelector dataSelectorFactory = new MockDataSelector();
  @Spy MockDeidentificator deidentificatorFactory = new MockDeidentificator();
  @Spy MockBundleSender bundleSenderFactory = new MockBundleSender();

  TransferProcessFactory factory;

  @BeforeEach
  void setUp() {
    var mapper = new ObjectMapper(new YAMLFactory()).registerModule(new JavaTimeModule());

    lenient()
        .doReturn(cohortSelectorFactory)
        .when(appContext)
        .getBean("mockCohortSelector", CohortSelector.Factory.class);
    lenient()
        .doReturn(dataSelectorFactory)
        .when(appContext)
        .getBean("mockDataSelector", DataSelector.Factory.class);
    lenient()
        .doReturn(deidentificatorFactory)
        .when(appContext)
        .getBean("mockDeidentificator", Deidentificator.Factory.class);
    lenient()
        .doReturn(bundleSenderFactory)
        .when(appContext)
        .getBean("mockBundleSender", BundleSender.Factory.class);

    factory = new TransferProcessFactory(appContext, mapper);
  }

  @Test
  void validConfigYieldsNoErrors() {
    var processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThat(factory.create(processDefinition, "example")).isNotNull();
  }

  @Test
  void implementationConfigIsGivenToImplementation() {
    var processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of("pids", List.of("value-125591"))),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThat(factory.create(processDefinition, "example")).isNotNull();

    var implConfig = new MockCohortSelector.Config(List.of("value-125591"));
    var commonConfig = new CohortSelector.Config();
    verify(cohortSelectorFactory).create(commonConfig, implConfig);
  }

  @Test
  void commonConfigIsGivenToImplementation() {
    var processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of()),
            Map.ofEntries(
                entry("mock", Map.of()), // Implementation config
                entry("ignoreConsent", true)), // Common config entry
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThat(factory.create(processDefinition, "example")).isNotNull();

    var implConfig = new MockDataSelector.Config();
    var commonConfig = new DataSelector.Config(true);
    verify(dataSelectorFactory).create(commonConfig, implConfig);
  }

  @Test
  void findImplFiltersOutCommonConfigKeys() {
    var config =
        Map.<String, Object>of(
            "shared", Map.of(),
            "mock", Map.of());

    Entry<String, ?> impl =
        TransferProcessFactory.findImpl(
            CohortSelector.class, CohortSelector.Factory.class, Set.of("shared"), config);

    assertThat(impl.getKey()).isEqualTo("mock");
    assertThat(impl.getValue()).isEqualTo(Map.of());
  }
}
