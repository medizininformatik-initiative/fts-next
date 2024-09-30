package care.smith.fts.cda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

@ExtendWith(MockitoExtension.class)
class ProjectReaderTest {

  @Mock TransferProcessFactory processFactory;
  @Mock ConfigurableListableBeanFactory beanFactory;

  private final ObjectMapper objectMapper;
  private final Path testDirectory = Paths.get("src/test/resources/projects");
  private @TempDir Path tempDirectory;

  public ProjectReaderTest() {
    objectMapper =
        new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Test
  void emptyDirYieldsNoBeans() throws Exception {
    var factory = new ProjectReader(processFactory, objectMapper, tempDirectory);

    factory.createTransferProcesses();

    verifyNoInteractions(processFactory);
    verifyNoInteractions(beanFactory);
  }

  @Test
  void testDirYieldsBeans() throws Exception {
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "example", pids -> null, c -> null, b -> null, b -> null);
    when(processFactory.create(any(), anyString())).thenReturn(process);

    var factory = new ProjectReader(processFactory, objectMapper, testDirectory);
    factory.createTransferProcesses();

    verify(processFactory, times(1)).create(any(), eq("example"));
  }
}
