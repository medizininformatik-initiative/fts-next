package care.smith.fts.packager.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TildeExpandingFileConverter.
 */
class TildeExpandingFileConverterTest {

  private TildeExpandingFileConverter converter;
  private String userHome;

  @BeforeEach
  void setUp() {
    converter = new TildeExpandingFileConverter();
    userHome = System.getProperty("user.home");
    assertThat(userHome).isNotNull(); // Ensure we have a user home for testing
  }

  @Test
  void shouldExpandTildeAlone() {
    String input = "~";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(userHome);
  }

  @Test
  void shouldExpandTildeSlashPath() {
    String input = "~/config/application.yaml";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(userHome + "/config/application.yaml");
  }

  @Test
  void shouldNotExpandPathsWithoutTilde() {
    String input = "/etc/config.yaml";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("/etc/config.yaml");
  }

  @Test
  void shouldNotExpandRelativePathsWithoutTilde() {
    String input = "config/application.yaml";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("config/application.yaml");
  }

  @Test
  void shouldHandleNullInput() {
    String input = null;

    File result = converter.convert(input);

    assertThat(result).isNull();
  }

  @Test
  void shouldHandleEmptyInput() {
    String input = "";

    File result = converter.convert(input);

    assertThat(result).isNull();
  }

  @Test
  void shouldNotExpandTildeWithUsernameFormat() {
    String input = "~otheruser/config.yaml";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("~otheruser/config.yaml");
  }

  @Test
  void shouldExpandTildeInComplexPath() {
    String input = "~/.config/fhir-packager/settings.yaml";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo(userHome + "/.config/fhir-packager/settings.yaml");
  }

  @Test
  void shouldHandleTildeInMiddleOfPath() {
    String input = "/some/path/~/config.yaml";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result.getPath()).isEqualTo("/some/path/~/config.yaml");
  }

  @Test
  void shouldCreateCorrectFileObject() {
    String input = "~/test.yaml";

    File result = converter.convert(input);

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(File.class);
    assertThat(result.getName()).isEqualTo("test.yaml");
    assertThat(result.getParent()).isEqualTo(userHome);
  }
}