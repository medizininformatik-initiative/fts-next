package care.smith.fts.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifhir.Profile.Rule;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class ProfileTest {

  private static final DeidentifhirHandler<Object> NOOP =
      (path, value, context) -> Optional.of(value);

  private static Profile profile(String hocon) {
    Registry registry = new Registry();
    registry.addHandler("noop", NOOP);
    Config config = ConfigFactory.parseString(hocon);
    return Profile.parse(config, registry);
  }

  @Test
  void answersKeepTransformAndRemoveForOneModule() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.gender", "Patient.birthDate"]
              paths { "Patient.birthDate" { handler = noop } }
            }
            """);
    Patient patient = new Patient();

    Rule kept = profile.ruleFor(patient, List.of("Patient", "gender"), DateType.class);
    Rule transformed = profile.ruleFor(patient, List.of("Patient", "birthDate"), DateType.class);
    Rule removed = profile.ruleFor(patient, List.of("Patient", "id"), DateType.class);

    assertThat(kept).isEqualTo(new Rule.Apply(List.of()));
    assertThat(transformed).isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(removed).isEqualTo(Rule.REMOVE);
  }

  /** A glob expands against the base list only; the star does not cross a dot. */
  @Test
  void aTrailingGlobExpandsAgainstTheBaseList() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.name.family", "Patient.name.given", "Patient.address.city"]
              paths { "Patient.name.*" { handler = noop } }
            }
            """);
    Patient patient = new Patient();

    assertThat(profile.ruleFor(patient, List.of("Patient", "name", "family"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(profile.ruleFor(patient, List.of("Patient", "name", "given"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(profile.ruleFor(patient, List.of("Patient", "address", "city"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of()));
  }

  @Test
  void aLeadingGlobMatchesAnyPrefix() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.address.city", "Patient.name.family"]
              paths { "*.city" { handler = noop } }
            }
            """);
    Patient patient = new Patient();

    assertThat(profile.ruleFor(patient, List.of("Patient", "address", "city"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(profile.ruleFor(patient, List.of("Patient", "name", "family"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of()));
  }

  @Test
  void aGlobInTheMiddleStaysWithinOnePathElement() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.name.family", "Patient.contact.name.family"]
              paths { "Patient.*.family" { handler = noop } }
            }
            """);
    Patient patient = new Patient();

    assertThat(profile.ruleFor(patient, List.of("Patient", "name", "family"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(
            profile.ruleFor(
                patient, List.of("Patient", "contact", "name", "family"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of()));
  }

  /** Choice elements put brackets into path keys; a glob treats them as literal text. */
  @Test
  void aGlobMatchesPathElementsThatContainRegexMetacharacters() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.deceased[dateTime]"]
              paths { "*.deceased[dateTime]" { handler = noop } }
            }
            """);

    Rule rule =
        profile.ruleFor(new Patient(), List.of("Patient", "deceased[dateTime]"), DateType.class);

    assertThat(rule).isEqualTo(new Rule.Apply(List.of(NOOP)));
  }

  /** A type handler applies to every element of its class and runs before the path handlers. */
  @Test
  void typeHandlersRunBeforePathHandlers() {
    DeidentifhirHandler<Object> forType = (path, value, context) -> Optional.of(value);
    DeidentifhirHandler<Object> forPath = (path, value, context) -> Optional.of(value);
    Registry registry = new Registry();
    registry.addHandler("forType", forType);
    registry.addHandler("forPath", forPath);
    Profile profile =
        Profile.parse(
            ConfigFactory.parseString(
                """
                modules.test {
                  pattern = "Patient.exists()"
                  base = ["Patient.birthDate"]
                  paths { "Patient.birthDate" { handler = forPath } }
                  types { DateType { handler = forType } }
                }
                """),
            registry);

    Rule rule = profile.ruleFor(new Patient(), List.of("Patient", "birthDate"), DateType.class);

    assertThat(rule).isEqualTo(new Rule.Apply(List.of(forType, forPath)));
  }

  @Test
  void aModuleWhosePatternDoesNotMatchTheResourceKeepsNothing() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.gender"]
            }
            """);

    Rule rule = profile.ruleFor(new Observation(), List.of("Patient", "gender"), DateType.class);

    assertThat(rule).isEqualTo(Rule.REMOVE);
  }

  @Test
  void anUnknownTypeInTheTypesSectionIsRejected() {
    assertThatThrownBy(
            () ->
                profile(
                    """
                    modules.test {
                      pattern = "Patient.exists()"
                      base = ["Patient.birthDate"]
                      types { NoSuchType { handler = noop } }
                    }
                    """))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("NoSuchType");
  }

  /** The stars of a glob may also match nothing, so they cover the named element itself. */
  @Test
  void aGlobStarAtEitherEndAlsoMatchesNoElementAtAll() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.name", "Patient.gender"]
              paths {
                "Patient.name.*" { handler = noop }
                "*.Patient.gender" { handler = noop }
              }
            }
            """);
    Patient patient = new Patient();

    assertThat(profile.ruleFor(patient, List.of("Patient", "name"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(profile.ruleFor(patient, List.of("Patient", "gender"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
  }

  /** Without a star at an end, a glob is anchored there and does not match a longer path. */
  @Test
  void aGlobWithoutAStarAtAnEndIsAnchoredAtThatEnd() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.name", "Patient.name.family", "Patient.contact.name.given"]
              paths {
                "*.name" { handler = noop }
                "name.*" { handler = noop }
              }
            }
            """);
    Patient patient = new Patient();

    assertThat(profile.ruleFor(patient, List.of("Patient", "name"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(profile.ruleFor(patient, List.of("Patient", "name", "family"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of()));
    assertThat(
            profile.ruleFor(
                patient, List.of("Patient", "contact", "name", "given"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of()));
  }

  /** The base list alone decides what is kept; a type handler only transforms what it keeps. */
  @Test
  void aTypeHandlerDoesNotKeepAnElementThatNoBasePathLists() {
    Profile profile =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = []
              types { DateType { handler = noop } }
            }
            """);

    Rule rule = profile.ruleFor(new Patient(), List.of("Patient", "birthDate"), DateType.class);

    assertThat(rule).isEqualTo(Rule.REMOVE);
  }

  /** A lone star names every base path; a lone element with a star stays within one element. */
  @Test
  void aSingleElementGlobExpandsAgainstTheBaseList() {
    Profile everything =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.gender", "Patient.birthDate"]
              paths { "*" { handler = noop } }
            }
            """);
    Profile noneWithoutADot =
        profile(
            """
            modules.test {
              pattern = "Patient.exists()"
              base = ["Patient.gender"]
              paths { "Pat*" { handler = noop } }
            }
            """);
    Patient patient = new Patient();

    assertThat(everything.ruleFor(patient, List.of("Patient", "gender"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(everything.ruleFor(patient, List.of("Patient", "birthDate"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(noneWithoutADot.ruleFor(patient, List.of("Patient", "gender"), DateType.class))
        .isEqualTo(new Rule.Apply(List.of()));
  }
}
