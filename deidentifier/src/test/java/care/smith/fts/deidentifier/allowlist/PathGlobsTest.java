package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PathGlobsTest {

  private static Set<String> paths(String commaSeparated) {
    return Set.of(commaSeparated.split(","));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "Patient.name.*; Patient.name.family,Patient.name.given,Patient.address.city;"
            + " Patient.name.family,Patient.name.given",
        "*.city; Patient.address.city,Patient.name.family; Patient.address.city",
        "Patient.*.family; Patient.name.family,Patient.contact.name.family; Patient.name.family",
        "Patient.gender.*; Patient.gender,Patient.gender.extension.url,Patient.birthDate;"
            + " Patient.gender,Patient.gender.extension.url",
        "*.Patient.gender; Patient.gender,Patient.gender.extension.url; Patient.gender",
        "*.gender; Patient.gender,Patient.gender.extension.url,Patient.contact.gender;"
            + " Patient.gender,Patient.contact.gender",
        "*.system; Patient.identifier.system,Patient.identifier.system.extension.url;"
            + " Patient.identifier.system",
        "*; Patient.gender,Patient.name.family; Patient.gender,Patient.name.family",
        "Patient.birth*; Patient.birthDate,Patient.birthDate.extension.url,Patient.name.family;"
            + " Patient.birthDate",
        "*.deceased[dateTime]; Patient.deceased[dateTime],Patient.deceased[boolean];"
            + " Patient.deceased[dateTime]",
        "Patient.gender*; Patient.gender,Patient.name.family; Patient.gender",
        "Patient.*; Patient.gender,Patient.name.family; Patient.gender,Patient.name.family",
        "a.b; a.b,a.bc,ab; a.b"
      })
  void expandsAKeyToTheBasePathsItMatches(String key, String base, String expected) {
    var keyPerPath = PathGlobs.keyPerPath(Set.of(key), paths(base));

    assertThat(keyPerPath.keySet()).containsExactlyInAnyOrder(expected.split(","));
    assertThat(keyPerPath.values()).containsOnly(key);
  }

  @ParameterizedTest
  @CsvSource({"Patient.address.*", ".", "*.*", "name.*", "Patient.na[m]e.*", "Patient.na.e.family"})
  void rejectsAKeyThatMatchesNoBasePath(String key) {
    assertThatThrownBy(() -> PathGlobs.keyPerPath(Set.of(key), Set.of("Patient.name.family")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The paths entry %s matches no path of the base list!".formatted(key));
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "plain and glob; Patient.name.family; Patient.name.*",
        "glob and glob; Patient.name.*; *.family"
      })
  void rejectsTwoKeysThatReachTheSameBasePath(String reason, String first, String second) {
    var base = Set.of("Patient.name.family", "Patient.name.given");

    assertThatThrownBy(() -> PathGlobs.keyPerPath(Set.of(first, second), base))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("The paths entries ")
        .hasMessageContaining(first)
        .hasMessageContaining(second)
        .hasMessageEndingWith(
            " both reach the path Patient.name.family, but a path has one handler!");
  }

  @Test
  void mapsEveryReachedBasePathToTheKeyThatReachesIt() {
    var keyPerPath =
        PathGlobs.keyPerPath(
            Set.of("Patient.name.*", "Patient.gender"),
            Set.of("Patient.name.family", "Patient.name.given", "Patient.gender", "Patient.id"));

    assertThat(keyPerPath)
        .isEqualTo(
            Map.of(
                "Patient.name.family", "Patient.name.*",
                "Patient.name.given", "Patient.name.*",
                "Patient.gender", "Patient.gender"));
  }
}
