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
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class HoconProfileTest {

  private static final DeidentifierHandler<Object> NOOP = (value, context) -> Optional.of(value);

  private static final Registry REGISTRY = new Registry();

  static {
    REGISTRY.addHandler("noop", Object.class, NOOP);
    REGISTRY.addHandler("primitive", PrimitiveType.class, (value, context) -> Optional.of(value));
    REGISTRY.addHandler("dateOnly", DateType.class, (value, context) -> Optional.of(value));
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
                Map.of("Patient.birthDate", List.of(REGISTRY.resolve("noop"))),
                Map.of()));
  }

  @Test
  void parsesTypeHandlersIntoAModule() {
    Profile profile =
        parse(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.birthDate"]
              types { DateType { handler = noop } }
            }
            """);

    assertThat(profile.modules())
        .containsExactly(
            new Module(
                new ResourceExistsPath("Patient"),
                Set.of("Patient.birthDate"),
                Map.of(),
                Map.of(DateType.class, List.of(REGISTRY.resolve("noop")))));
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
            new Module(
                new ResourceExistsPath("Patient"), Set.of("Patient.gender"), Map.of(), Map.of()));
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
                Map.of("Patient.birthDate", List.of(REGISTRY.resolve("noop"))),
                Map.of()),
            new Module(
                new ResourceExistsPath("Observation"),
                Set.of("Observation.status"),
                Map.of(),
                Map.of()));
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
            new Module(
                new ResourceExistsPath("Patient"), Set.of("Patient.gender"), Map.of(), Map.of()));
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
  void rejectsATypesEntryThatNamesNoHapiClassKeepingTheCause() {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.birthDate"]
          types { NoSuchType { handler = noop } }
        }
        """;

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NoSuchType")
        .hasMessageContaining("types")
        .hasCauseInstanceOf(ClassNotFoundException.class);
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"PrimitiveType", "BaseDateTimeType", "Coding", "Enumerations"})
  void rejectsATypesEntryThatIsNoConcretePrimitiveTypeNamingIt(String type) {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.birthDate"]
          types { %s { handler = noop } }
        }
        """
            .formatted(type);

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("types entry " + type + " ")
        .hasMessageContaining("not a concrete FHIR primitive type")
        .hasMessageContaining("never matches an element");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"Enumeration", "DateTimeType"})
  void acceptsATypesEntryThatIsAConcretePrimitiveType(String type) throws ClassNotFoundException {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.birthDate"]
          types { %s { handler = noop } }
        }
        """
            .formatted(type);

    assertThat(parse(hocon).modules().getFirst().typeHandlers())
        .containsOnlyKeys(Class.forName("org.hl7.fhir.r4.model." + type))
        .containsValue(List.of(REGISTRY.resolve("noop")));
  }

  @Test
  void rejectsAHandlerThatDoesNotWorkOnItsTypeNamingHandlerTypesAndTypesSection() {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.birthDate"]
          types { StringType { handler = dateOnly } }
        }
        """;

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Handler 'dateOnly' works on DateType, not on StringType")
        .hasMessageContaining("(types section)");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "same type; DateType; dateOnly",
        "supertype; DateType; primitive",
        "Object; StringType; noop"
      })
  void acceptsAHandlerThatWorksOnItsType(String reason, String type, String handler)
      throws ClassNotFoundException {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.birthDate"]
          types { %s { handler = %s } }
        }
        """
            .formatted(type, handler);

    assertThat(parse(hocon).modules().getFirst().typeHandlers())
        .containsOnlyKeys(Class.forName("org.hl7.fhir.r4.model." + type))
        .containsValue(List.of(REGISTRY.resolve(handler)));
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
  @ValueSource(
      strings = {
        "Patient.birthdate",
        "Foo.bar",
        "Patient.deceased",
        "Patient.gender.extension.value"
      })
  void rejectsABasePathThatNamesNoFhirElementNamingThePath(String path) {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.gender", "%s"]
        }
        """
            .formatted(path);

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("names no FHIR element")
        .hasMessageContaining(path);
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
        "*.Patient.gender; Patient.gender,Patient.gender.extension; Patient.gender",
        "*.gender; Patient.gender,Patient.gender.extension,Patient.contact.gender;"
            + " Patient.gender,Patient.contact.gender",
        "*.name; Patient.name.family,Patient.contact.name; Patient.contact.name",
        "*; Patient.gender,Patient.name.family; Patient.gender,Patient.name.family",
        "Patient.birth*; Patient.birthDate,Patient.birthDate.extension,Patient.name;"
            + " Patient.birthDate",
        "*.deceased[dateTime]; Patient.deceased[dateTime],Patient.deceased[boolean];"
            + " Patient.deceased[dateTime]",
        "Patient.gender*; Patient.gender,Patient.name; Patient.gender",
        "Patient.*; Patient.gender,Patient.gender; Patient.gender"
      })
  void expandsAGlobAgainstTheBasePaths(String glob, String base, String expectedMatches) {
    assertThat(handledPaths(glob, base)).containsExactlyInAnyOrder(expectedMatches.split(","));
  }

  @ParameterizedTest
  @CsvSource({"Patient.address.*", ".", "*.*", "name.*", "Patient.na[m]e.*", "Patient.na.e.family"})
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

  @Test
  void rejectsAHandlerThatDoesNotWorkOnTheTypeOfItsPathNamingHandlerTypesAndPath() {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.gender"]
          paths { "Patient.gender" { handler = dateOnly } }
        }
        """;

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dateOnly")
        .hasMessageContaining("DateType")
        .hasMessageContaining("Enumeration")
        .hasMessageContaining("Patient.gender");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "same type; dateOnly; Patient.birthDate",
        "supertype; primitive; Patient.birthDate",
        "Object; noop; Patient.gender"
      })
  void acceptsAHandlerThatWorksOnTheTypeOfItsPath(String reason, String handler, String path) {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["%2$s"]
          paths { "%2$s" { handler = %1$s } }
        }
        """
            .formatted(handler, path);

    assertThat(parse(hocon).modules().getFirst().pathHandlers())
        .containsOnlyKeys(path)
        .containsEntry(path, List.of(REGISTRY.resolve(handler)));
  }

  @Test
  void checksTheHandlerTypeAgainstEveryPathAGlobReachesNamingTheNonFittingPath() {
    String hocon =
        """
        modules.patient {
          pattern = "Patient.exists()"
          base = ["Patient.birthDate", "Patient.gender"]
          paths { "Patient.*" { handler = dateOnly } }
        }
        """;

    assertThatThrownBy(() -> parse(hocon))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("dateOnly")
        .hasMessageContaining("(Patient.gender)");
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
