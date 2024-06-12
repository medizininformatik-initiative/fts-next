package care.smith.fts.test;

import care.smith.fts.test.FhirGenerator.Fixed;
import care.smith.fts.test.FhirGenerator.Increasing;
import care.smith.fts.test.FhirGenerator.OneOfList;
import java.io.IOException;
import java.util.List;
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

  public static Bundle generatePatients(
      String idPrefix, List<String> years, String identifierSystem) throws IOException {
    FhirGenerator fhirGenerator = new FhirGenerator("PatientTemplate.json");
    fhirGenerator.replaceTemplateFieldWith("$PATIENT_ID", new Increasing(idPrefix));
    fhirGenerator.replaceTemplateFieldWith("$IDENTIFIER_SYSTEM", new Fixed(identifierSystem));
    fhirGenerator.replaceTemplateFieldWith("$YEAR", new OneOfList(years));
    return fhirGenerator.generateBundle(1, 100);
  }
}
