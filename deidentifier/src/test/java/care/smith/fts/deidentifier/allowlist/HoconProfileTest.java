package care.smith.fts.deidentifier.allowlist;

import static java.util.stream.Collectors.joining;
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
import java.util.stream.Stream;
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

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "Patient.name.*; Patient.name.family,Patient.name.given,Patient.address.city;"
            + " Patient.name.family,Patient.name.given",
        "*.city; Patient.address.city,Patient.name.family; Patient.address.city",
        "Patient.*.family; Patient.name.family,Patient.contact.name.family; Patient.name.family",
        "Patient.name.*; Patient.name,Patient.name.family,Patient.gender;"
            + " Patient.name,Patient.name.family",
        "*.Patient.gender; Patient.gender,Patient.gender.extension,X.Patient.gender;"
            + " Patient.gender,X.Patient.gender",
        "*.name; Patient.name.family,Patient.contact.name; Patient.contact.name",
        "name.*; Patient.contact.name.given,name.given; name.given",
        "*; Patient.gender,Patient.name.family; Patient.gender,Patient.name.family",
        "Patient.birth*; Patient.birthDate,Patient.birthDate.extension,Patient.name;"
            + " Patient.birthDate",
        "*.deceased[dateTime]; Patient.deceased[dateTime],Patient.deceasedd;"
            + " Patient.deceased[dateTime]",
        "Patient.gender*; Patient.gender,PatientXgender; Patient.gender",
        "Patient.*; Patient.gender,Patient.gender; Patient.gender"
      })
  void expandsAGlobAgainstTheBasePaths(String glob, String base, String expectedMatches) {
    assertThat(handledPaths(glob, base)).containsExactlyInAnyOrder(expectedMatches.split(","));
  }

  @ParameterizedTest
  @CsvSource({"Patient.address.*", ".", "*.*"})
  void rejectsAGlobThatMatchesNoBasePathNamingTheGlob(String glob) {
    assertThatThrownBy(() -> handledPaths(glob, "Patient.name.family"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("matches no path")
        .hasMessageContaining(glob);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "plain and glob; Patient.name.family; Patient.name.*; Patient.name.family",
        "glob and glob; Patient.name.*; *.family; Patient.name.family"
      })
  void rejectsTwoPathsEntriesReachingTheSameBasePathNamingIt(
      String reason, String first, String second, String expectedInMessage) {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.name.family", "Patient.name.given"]
          paths {
            "%s" { handler = noop }
            "%s" { handler = noop }
          }
        }
        """
            .formatted(first, second);

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(expectedInMessage)
        .hasMessageContaining(first)
        .hasMessageContaining(second);
  }

  /** The paths that the handler of one {@code paths} key ends up on, given a comma-list base. */
  private static Set<String> handledPaths(String key, String base) {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = [%s]
          paths { "%s" { handler = noop } }
        }
        """
            .formatted(
                Stream.of(base.split(",")).map(p -> "\"" + p + "\"").collect(joining(",")), key);
    return parse(hocon).modules().getFirst().pathHandlers().keySet();
  }
}
