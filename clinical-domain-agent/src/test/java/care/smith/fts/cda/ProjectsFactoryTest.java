package care.smith.fts.cda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

@ExtendWith(MockitoExtension.class)
class ProjectsFactoryTest {

  @Mock TransferProcessFactory<Bundle> processFactory;
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
    TransferProcess<Bundle> process =
        new TransferProcess<>(
            "test",
            () -> null,
            consentedPatient -> null,
            (patientBundle) -> null,
            (transportBundle) -> null);
    when(processFactory.create(any(), anyString())).thenReturn(process);

    var factory = new ProjectsFactory(processFactory, beanFactory, objectMapper, testDirectory);
    factory.registerProcesses();

    verify(beanFactory, times(1)).registerSingleton("example", process);
  }
}
