package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.Registry;
import care.smith.fts.deidentifier.allowlist.FhirPathPattern.ResourceExistsPath;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HoconProfileTest {

  private static final DeidentifierHandler<Object> NOOP = (value, context) -> Optional.of(value);

  private static final Registry REGISTRY = new Registry();

  static {
    REGISTRY.addHandler("noop", Object.class, NOOP);
  }

  private static Profile parse(String hocon) {
    return HoconProfile.parse(ConfigFactory.parseString(hocon), REGISTRY);
  }

  @Test
  void parsesPatternBaseAndPathHandlersIntoAModule() {
    Profile profile =
        parse(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.gender", "Patient.birthDate", "Patient.gender"]
              paths { "Patient.birthDate" { handler = noop } }
            }
            """);

    assertThat(profile.modules())
        .containsExactly(
            new Module(
                new ResourceExistsPath("Patient"),
                Set.of("Patient.gender", "Patient.birthDate"),
                Map.of("Patient.birthDate", List.of(REGISTRY.resolve("noop")))));
  }

  @Test
  void parsesAModuleWithoutPaths() {
    Profile profile =
        parse(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.gender"]
            }
            """);

    assertThat(profile.modules())
        .containsExactly(
            new Module(new ResourceExistsPath("Patient"), Set.of("Patient.gender"), Map.of()));
  }

  @Test
  void parsesEveryModule() {
    Profile profile =
        parse(
            """
            modules {
              patient {
                pattern = "Patient.exists()"
                base = ["Patient.birthDate"]
                paths { "Patient.birthDate" { handler = noop } }
              }
              observation {
                pattern = "Observation.exists()"
                base = ["Observation.status"]
              }
            }
            """);

    assertThat(profile.modules())
        .containsExactlyInAnyOrder(
            new Module(
                new ResourceExistsPath("Patient"),
                Set.of("Patient.birthDate"),
                Map.of("Patient.birthDate", List.of(REGISTRY.resolve("noop")))),
            new Module(
                new ResourceExistsPath("Observation"), Set.of("Observation.status"), Map.of()));
  }

  @Test
  void parsesAModuleWhoseNameContainsADot() {
    Profile profile =
        parse(
            """
            modules { "person-1.0.14" {
              pattern = "Patient.exists()"
              base = ["Patient.gender"]
            } }
            """);

    assertThat(profile.modules())
        .containsExactly(
            new Module(new ResourceExistsPath("Patient"), Set.of("Patient.gender"), Map.of()));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "unknown handler; Patient.id; doesNotExist; doesNotExist",
        "path not in base; Patient.birthDate; noop; Patient.birthDate"
      })
  void rejectsAPathsEntryAtParseTime(
      String reason, String path, String handler, String expectedInMessage) {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.id"]
          paths { "%s" { handler = %s } }
        }
        """
            .formatted(path, handler);

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(expectedInMessage);
  }

  @Test
  void rejectsAPathsEntryThatIsNotAnObjectNamingThePath() {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.id"]
          paths { "Patient.id" = noop }
        }
        """;

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(ConfigException.WrongType.class)
        .hasMessageContaining("Patient.id");
  }
}
