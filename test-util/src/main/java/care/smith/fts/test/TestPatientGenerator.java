package care.smith.fts.test;

import care.smith.fts.test.FhirGenerator.Fixed;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;

public class TestPatientGenerator {
  public static Bundle generateOnePatient(String id, String year, String identifierSystem)
      throws IOException {
    FhirGenerator fhirGenerator = new FhirGenerator("PatientTemplate.json");
    fhirGenerator.replaceTemplateFieldWith("$PATIENT_ID", new Fixed(id));
    fhirGenerator.replaceTemplateFieldWith("$IDENTIFIER_SYSTEM", new Fixed(identifierSystem));
    fhirGenerator.replaceTemplateFieldWith("$YEAR", new Fixed(year));
    return fhirGenerator.generateBundle(1, 100);
  }
}
