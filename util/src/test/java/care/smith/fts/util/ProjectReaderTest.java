package care.smith.fts.util;

import static java.nio.file.Files.createDirectory;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class ProjectReaderTest {

  record TestConfig(String greeting) {}

  record TestProcess(TestConfig config, String name) {}

  private static final BiFunction<TestConfig, String, TestProcess> FAILING_FACTORY =
      (config, name) -> {
        throw new IllegalArgumentException(name);
      };

  private final ObjectMapper objectMapper = TransferProcessObjectMapper.create();

  private @TempDir Path tempDirectory;

  private ProjectReader<TestConfig, TestProcess> reader(
      BiFunction<TestConfig, String, TestProcess> processFactory, boolean strictValidation) {
    return new ProjectReader<>(
        processFactory, objectMapper, TestConfig.class, tempDirectory, strictValidation);
  }

  @Test
  void emptyDirYieldsNoProcesses() throws Exception {
    assertThat(reader(TestProcess::new, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void projectFileYieldsProcessNamedAfterFile() throws Exception {
    writeString(tempDirectory.resolve("example.yaml"), "greeting: moin");

    assertThat(reader(TestProcess::new, false).createTransferProcesses())
        .containsExactly(new TestProcess(new TestConfig("moin"), "example"));
  }

  @Test
  void filesWithOtherExtensionsIgnored() throws Exception {
    writeString(tempDirectory.resolve("notes.txt"), "greeting: moin");

    assertThat(reader(TestProcess::new, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void backupOfProjectFileIgnored() throws Exception {
    writeString(tempDirectory.resolve("foo.yaml.bak"), "greeting: moin");

    assertThat(reader(TestProcess::new, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void backupOfProjectFileIgnoredWhenStrict() throws Exception {
    writeString(tempDirectory.resolve("foo.yaml.bak"), "greeting: moin");

    assertThat(reader(TestProcess::new, true).createTransferProcesses()).isEmpty();
  }

  @Test
  void directoryNamedLikeProjectFileIgnored() throws Exception {
    createDirectory(tempDirectory.resolve("directory.json"));

    assertThat(reader(TestProcess::new, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void directoryNamedLikeProjectFileFailsWhenStrict() throws Exception {
    createDirectory(tempDirectory.resolve("directory.json"));

    assertThatThrownBy(reader(TestProcess::new, true)::createTransferProcesses)
        .isInstanceOf(ProjectConfigurationException.class)
        .hasMessageContaining("is not a regular file");
  }

  @Test
  void unreadableFileIgnored() throws Exception {
    var testFile = tempDirectory.resolve("unreadable.yaml");
    writeString(testFile, "greeting: moin");
    assumeUnreadable(testFile);

    assertThat(reader(TestProcess::new, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void unreadableFileFailsWhenStrict() throws Exception {
    var testFile = tempDirectory.resolve("unreadable.yaml");
    writeString(testFile, "greeting: moin");
    assumeUnreadable(testFile);

    assertThatThrownBy(reader(TestProcess::new, true)::createTransferProcesses)
        .isInstanceOf(ProjectConfigurationException.class)
        .hasMessageContaining("is not readable");
  }

  /** Root ignores file permissions, so skip the test instead of asserting a false expectation. */
  private static void assumeUnreadable(Path file) throws IOException {
    setPosixFilePermissions(file, EnumSet.noneOf(PosixFilePermission.class));
    assumeThat(Files.isReadable(file)).isFalse();
  }

  @Test
  void validProjectCreatedAlongsideInvalidOne() throws Exception {
    writeString(tempDirectory.resolve("example.yaml"), "greeting: moin");
    writeString(tempDirectory.resolve("broken.yaml"), "greeting: moin\nunknown: bar");
    createDirectory(tempDirectory.resolve("directory.json"));

    assertThat(reader(TestProcess::new, false).createTransferProcesses())
        .containsExactly(new TestProcess(new TestConfig("moin"), "example"));
  }

  @Test
  void fileWithoutProjectNameIgnored() throws Exception {
    writeString(tempDirectory.resolve(".yaml"), "greeting: moin");

    assertThat(reader(TestProcess::new, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void fileWithoutProjectNameFailsWhenStrict() throws Exception {
    writeString(tempDirectory.resolve(".yaml"), "greeting: moin");

    assertThatThrownBy(reader(TestProcess::new, true)::createTransferProcesses)
        .isInstanceOf(ProjectConfigurationException.class)
        .hasMessageContaining("Could not determine project name");
  }

  @Test
  void invalidProjectNotCreated() throws Exception {
    writeString(tempDirectory.resolve("example.yaml"), "greeting: moin");

    assertThat(reader(FAILING_FACTORY, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void invalidProjectFailsWhenStrict() throws Exception {
    writeString(tempDirectory.resolve("fail-project.yaml"), "greeting: moin");

    assertThatThrownBy(reader(FAILING_FACTORY, true)::createTransferProcesses)
        .isInstanceOf(ProjectConfigurationException.class)
        .hasMessageContaining("fail-project");
  }

  @Test
  void deletedFileNotCreated() throws Exception {
    writeString(tempDirectory.resolve("delete-me.json"), "greeting: moin");

    var reader =
        new ProjectReader<TestConfig, TestProcess>(
            FAILING_FACTORY, objectMapper, TestConfig.class, tempDirectory, false) {
          @Override
          protected Optional<TestProcess> openConfigAndParse(Path projectFile, String name) {
            try {
              delete(projectFile);
            } catch (IOException e) {
              throw new IllegalStateException(e);
            }
            return super.openConfigAndParse(projectFile, name);
          }
        };

    assertThat(reader.createTransferProcesses()).isEmpty();
  }

  @Test
  void unknownKeyInConfigRejected() throws Exception {
    writeString(tempDirectory.resolve("unknown-key.yaml"), "greeting: moin\nunknown: bar");

    assertThat(reader(TestProcess::new, false).createTransferProcesses()).isEmpty();
  }

  @Test
  void unknownKeyInConfigFailsWhenStrict() throws Exception {
    writeString(tempDirectory.resolve("unknown-key.yaml"), "greeting: moin\nunknown: bar");

    assertThatThrownBy(reader(TestProcess::new, true)::createTransferProcesses)
        .isInstanceOf(ProjectConfigurationException.class)
        .hasMessageContaining("Unable to parse 'unknown-key' project's configuration");
  }
}
