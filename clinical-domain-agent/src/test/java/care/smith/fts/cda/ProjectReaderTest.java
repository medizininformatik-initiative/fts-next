package care.smith.fts.cda;

import static java.nio.file.Files.delete;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    objectMapper = new ObjectMapper(new YAMLFactory());
  }

  @Test
  void emptyDirYieldsNoBeans() throws Exception {
    var reader = new ProjectReader(processFactory, objectMapper, tempDirectory, false);

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

    var reader = new ProjectReader(processFactory, objectMapper, testDirectory, false);
    var transferProcesses = reader.createTransferProcesses();

    verify(processFactory, times(1)).create(any(), eq("example"));
    assertThat(transferProcesses).hasSize(1);
  }

  @Test
  void invalidProjectNotCreated() throws Exception {
    when(processFactory.create(any(), eq("example"))).thenThrow(IllegalArgumentException.class);

    var reader = new ProjectReader(processFactory, objectMapper, testDirectory, false);
    var transferProcesses = reader.createTransferProcesses();

    verify(processFactory, times(1)).create(any(), eq("example"));
    assertThat(transferProcesses).hasSize(0);
  }

  @Test
  void invalidProjectFailsWhenStrict() throws Exception {
    var testFile = tempDirectory.resolve("fail-project.yaml");
    writeString(
        testFile,
        """
        cohortSelector:
          mock: {}
        dataSelector:
          mock: {}
        deidentificator:
          mock: {}
        bundleSender:
          mock: {}
        """);

    when(processFactory.create(any(), eq("fail-project")))
        .thenThrow(IllegalArgumentException.class);

    var reader = new ProjectReader(processFactory, objectMapper, tempDirectory, true);

    assertThatThrownBy(reader::createTransferProcesses)
        .isInstanceOf(ProjectConfigurationException.class)
        .hasMessageContaining("fail-project");
  }

  @Test
  void deletedFileNotCreated() throws IOException {
    var testFile = tempDirectory.resolve("delete-me.json");
    writeString(testFile, "test content");

    var reader =
        new ProjectReader(processFactory, objectMapper, tempDirectory, false) {
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

  @Test
  void unknownKeyInConfigRejected() throws Exception {
    var testFile = tempDirectory.resolve("unknown-key.yaml");
    writeString(
        testFile,
        """
        cohortSelector:
          mock: {}
        dataSelector:
          mock: {}
        deidentificator:
          mock: {}
        bundleSender:
          mock: {}
        unknownSection:
          foo: bar
        """);

    var reader = new ProjectReader(processFactory, objectMapper, tempDirectory, false);
    var transferProcesses = reader.createTransferProcesses();

    assertThat(transferProcesses).hasSize(0);
  }

  @Test
  void unknownKeyInConfigFailsWhenStrict() throws Exception {
    var testFile = tempDirectory.resolve("unknown-key-strict.yaml");
    writeString(
        testFile,
        """
        cohortSelector:
          mock: {}
        dataSelector:
          mock: {}
        deidentificator:
          mock: {}
        bundleSender:
          mock: {}
        unknownSection:
          foo: bar
        """);

    var reader = new ProjectReader(processFactory, objectMapper, tempDirectory, true);

    assertThatThrownBy(reader::createTransferProcesses)
        .isInstanceOf(ProjectConfigurationException.class);
  }
}
