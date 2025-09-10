package care.smith.fts.test;

import static care.smith.fts.test.FhirGenerators.patient;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;

import java.io.IOException;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;

public class TestPatientGenerator {
  public static Bundle generateOnePatient(
      String id, String year, String identifierSystem, String patientIdentifier)
      throws IOException {
    return Stream.of(
            patient(() -> id, () -> identifierSystem, () -> patientIdentifier, () -> year)
                .generateResource())
        .collect(toBundle())
        .setTotal(1);
  }
}
