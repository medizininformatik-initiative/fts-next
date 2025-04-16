package care.smith.fts.cda;

import static java.nio.file.Files.delete;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
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
  private final Path testDirectory = Paths.get("src/test/resources/more-projects");
  private @TempDir Path tempDirectory;

  public ProjectReaderTest() {
    objectMapper =
        new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @Test
  void emptyDirYieldsNoBeans() throws Exception {
    var reader = new ProjectReader(processFactory, objectMapper, tempDirectory);

    var transferProcesses = reader.createTransferProcesses();

    verifyNoInteractions(processFactory);
    verifyNoInteractions(beanFactory);
    assertThat(transferProcesses).hasSize(0);
  }

  @Test
  void testDirYieldsBeans() throws Exception {
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "example",
            new TransferProcessConfig(null, null, null, null),
            pids -> null,
            c -> null,
            b -> null,
            b -> null);
    when(processFactory.create(any(), eq("example"))).thenReturn(process);

    var reader = new ProjectReader(processFactory, objectMapper, testDirectory);
    var transferProcesses = reader.createTransferProcesses();

    verify(processFactory, times(1)).create(any(), eq("example"));
    assertThat(transferProcesses).hasSize(1);
  }

  @Test
  void invalidProjectNotCreated() throws Exception {
    when(processFactory.create(any(), eq("example"))).thenThrow(IllegalArgumentException.class);

    var reader = new ProjectReader(processFactory, objectMapper, testDirectory);
    var transferProcesses = reader.createTransferProcesses();

    verify(processFactory, times(1)).create(any(), eq("example"));
    assertThat(transferProcesses).hasSize(0);
  }

  @Test
  void deletedFileNotCreated() throws IOException {
    var testFile = tempDirectory.resolve("delete-me.json");
    writeString(testFile, "test content");

    var reader =
        new ProjectReader(processFactory, objectMapper, tempDirectory) {
          @Override
          protected Optional<TransferProcessDefinition> openConfigAndParse(
              Path projectFile, String name) {
            try {
              delete(projectFile);
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
            return super.openConfigAndParse(projectFile, name);
          }
        };

    var transferProcesses = reader.createTransferProcesses();

    verify(processFactory, times(0)).create(any(), eq("delete-me"));
    assertThat(transferProcesses).hasSize(0);
  }
}
