package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.deidentifier.Registry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Optional;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class AllowListTest {

  private static final Config CONFIG =
      ConfigFactory.parseString(
          """
          deidentiFHIR.profile.version=0.2
          modules.patient {
            pattern = "Patient.exists()"
            base = ["Patient.id", "Patient.name.family"]
            paths { "Patient.name.family" { handler = redact } }
          }
          """);

  private static Patient patient() {
    Patient patient = new Patient();
    patient.setId("123");
    patient.setBirthDateElement(new DateType("1970-05-12"));
    patient.addName(new HumanName().setFamily("Doe").addGiven("Jane"));
    return patient;
  }

  @Test
  void keepsTheBaseAndAppliesThePathHandlers() {
    Registry registry = new Registry();
    registry.addHandler(
        "redact", StringType.class, (value, context) -> Optional.of(new StringType("X")));

    Patient result =
        (Patient) AllowList.fromConfig(CONFIG, registry).deidentify(patient()).orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.getNameFirstRep().getFamily()).isEqualTo("X");
    assertThat(result.getNameFirstRep().hasGiven()).isFalse();
    assertThat(result.hasBirthDate()).isFalse();
  }

  @Test
  void keepsTheBaseWithoutARegistry() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id"]
            }
            """);

    Patient result = (Patient) AllowList.fromConfig(config).deidentify(patient()).orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.hasName()).isFalse();
  }

  @Test
  void appliesATypeHandlerToEveryKeptElementOfThatClass() {
    Config config =
        ConfigFactory.parseString(
            """
            modules.patient {
              pattern = "Patient.exists()"
              base = ["Patient.id", "Patient.birthDate"]
              types { DateType { handler = firstOfYear } }
            }
            """);
    Registry registry = new Registry();
    registry.addHandler(
        "firstOfYear", DateType.class, (value, context) -> Optional.of(new DateType("1970-01-01")));

    Patient result =
        (Patient) AllowList.fromConfig(config, registry).deidentify(patient()).orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-01-01");
  }
}
