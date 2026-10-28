package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.deidentifier.Registry;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
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
        (Patient) AllowList.fromConfig(CONFIG, registry).deidentify(patient(), "p").orElseThrow();

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

    Patient result =
        (Patient) AllowList.fromConfig(config).deidentify(patient(), "p").orElseThrow();

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
        (Patient) AllowList.fromConfig(config, registry).deidentify(patient(), "p").orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("123");
    assertThat(result.getBirthDateElement().getValueAsString()).isEqualTo("1970-01-01");
  }

  private static final Config ID_ONLY =
      ConfigFactory.parseString(
          """
          modules.patient {
            pattern = "Patient.exists()"
            base = ["Patient.id"]
          }
          """);

  private static Bundle patientBundleWithConditionalRequest() {
    var bundle = new Bundle();
    var entry = bundle.addEntry().setResource(patient());
    entry.getRequest().setMethod(HTTPVerb.PUT).setUrl("Patient?identifier=sys|12345");
    return bundle;
  }

  @Test
  void replacesTheRequestUrlWithTheReferenceHandlerOfTheRegistry() {
    var registry = new Registry();
    registry.addHandler(
        Registry.REFERENCE_HANDLER_NAME,
        StringType.class,
        (value, context) -> Optional.of(new StringType("Patient/p-1")));

    var result =
        AllowList.fromConfig(ID_ONLY, registry)
            .deidentifyBundle(patientBundleWithConditionalRequest(), "p");

    assertThat(result.getEntryFirstRep().getRequest().getUrl()).isEqualTo("Patient/p-1");
  }

  @Test
  void dropsTheRequestUrlWithoutAReferenceHandler() {
    var result =
        AllowList.fromConfig(ID_ONLY, new Registry())
            .deidentifyBundle(patientBundleWithConditionalRequest(), "p");

    assertThat(result.getEntryFirstRep().hasRequest()).isFalse();
    assertThat(result.getEntryFirstRep().hasResource()).isTrue();
  }
}
