package care.smith.fts.cda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import care.smith.fts.cda.impl.MockBundleSender;
import care.smith.fts.cda.impl.MockCohortSelector;
import care.smith.fts.cda.impl.MockDataSelector;
import care.smith.fts.cda.impl.MockDeidentificationProvider;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

@ExtendWith(MockitoExtension.class)
class ProjectsFactoryTest {

  @Mock TransferProcessFactory processFactory;
  @Mock ConfigurableListableBeanFactory beanFactory;

  private final ObjectMapper objectMapper;
  private final Path testDirectory = Paths.get("src/test/resources/projects");
  private @TempDir Path tempDirectory;

  public ProjectsFactoryTest() {
    objectMapper =
        new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Test
  void emptyDirYieldsNoBeans() throws Exception {
    var factory = new ProjectsFactory(processFactory, beanFactory, objectMapper, tempDirectory);

    factory.registerProcesses();

    verifyNoInteractions(processFactory);
    verifyNoInteractions(beanFactory);
  }

  @Test
  void testDirYieldsBeans() throws Exception {
    TransferProcess process =
        new TransferProcess(
            new MockCohortSelector.Impl(new MockCohortSelector.Config(List.of())),
            new MockDataSelector.Impl(),
            new MockDeidentificationProvider.Impl(new MockDeidentificationProvider.Config(false)),
            new MockBundleSender.Impl(new MockBundleSender.Config(Set.of())));
    when(processFactory.create(any())).thenReturn(process);

    var factory = new ProjectsFactory(processFactory, beanFactory, objectMapper, testDirectory);
    factory.registerProcesses();

    verify(beanFactory, times(1)).registerSingleton("example", process);
  }
}
