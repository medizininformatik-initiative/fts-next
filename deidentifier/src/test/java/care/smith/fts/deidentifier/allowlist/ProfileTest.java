package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.Registry;
import care.smith.fts.deidentifier.Rule;
import care.smith.fts.deidentifier.allowlist.FhirPathPattern.ResourceExistsPath;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

class ProfileTest {

  private static final DeidentifierHandler<Object> NOOP = (value, context) -> Optional.of(value);
  private static final DeidentifierHandler<Object> OTHER = (value, context) -> Optional.of(value);

  private static final FhirPathPattern PATIENT = new ResourceExistsPath("Patient");

  private static Registry.Registration registration(DeidentifierHandler<Object> handler) {
    return new Registry.Registration(Object.class, handler, false);
  }

  private static Rule ruleFor(Profile profile, Resource resource, String path) {
    return profile.rulesFor(resource).ruleFor(List.of(path.split("\\.")), new DateType());
  }

  @Test
  void answersKeepTransformAndRemoveForOneModule() {
    Profile profile =
        new Profile(
            List.of(
                new Module(
                    PATIENT,
                    Set.of("Patient.gender", "Patient.birthDate"),
                    Map.of("Patient.birthDate", List.of(registration(NOOP))))));
    Patient patient = new Patient();

    assertThat(ruleFor(profile, patient, "Patient.gender")).isEqualTo(new Rule.Apply(List.of()));
    assertThat(ruleFor(profile, patient, "Patient.birthDate"))
        .isEqualTo(new Rule.Apply(List.of(NOOP)));
    assertThat(ruleFor(profile, patient, "Patient.id")).isEqualTo(Rule.REMOVE);
  }

  @Test
  void aModuleWhosePatternDoesNotMatchTheResourceKeepsNothing() {
    Profile profile = new Profile(List.of(new Module(PATIENT, Set.of("Patient.gender"), Map.of())));

    assertThat(ruleFor(profile, new Observation(), "Patient.gender")).isEqualTo(Rule.REMOVE);
  }

  @Test
  void mergesTheHandlerChainsOfAllMatchedModules() {
    Profile profile =
        new Profile(
            List.of(
                new Module(
                    PATIENT,
                    Set.of("Patient.birthDate"),
                    Map.of("Patient.birthDate", List.of(registration(NOOP)))),
                new Module(
                    PATIENT,
                    Set.of("Patient.birthDate"),
                    Map.of("Patient.birthDate", List.of(registration(OTHER))))));

    assertThat(ruleFor(profile, new Patient(), "Patient.birthDate"))
        .isInstanceOfSatisfying(
            Rule.Apply.class,
            apply -> assertThat(apply.handlers()).containsExactlyInAnyOrder(NOOP, OTHER));
  }

  @Test
  void aModuleRejectsAHandlerOnAPathThatIsNotInItsBase() {
    Map<String, List<Registry.Registration>> pathHandlers =
        Map.of("Patient.birthDate", List.of(registration(NOOP)));

    assertThatThrownBy(() -> new Module(PATIENT, Set.of("Patient.id"), pathHandlers))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Patient.birthDate");
  }
}
