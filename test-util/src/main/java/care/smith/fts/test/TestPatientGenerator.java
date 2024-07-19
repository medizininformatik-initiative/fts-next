package care.smith.fts.test;

import care.smith.fts.test.FhirGenerator.Incrementing;
import java.io.IOException;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;

public class TestPatientGenerator {
  public static Bundle generateOnePatient(String id, String year, String identifierSystem)
      throws IOException {
    return FhirGenerator.patient(() -> id, () -> identifierSystem, () -> year)
        .generateBundle(1, 100);
  }

  public static BundleAndIds generateNPatients(
      String idPrefix, String year, String identifierSystem, int n) throws IOException {
    var gen =
        FhirGenerator.patient(
            Incrementing.withPrefix(idPrefix), () -> identifierSystem, () -> year);
    Bundle bundle = gen.generateBundle(n, n);
    List<String> ids = gen.getReplacements().get("$PATIENT_ID");
    return new BundleAndIds(bundle, ids);
  }

  public record BundleAndIds(Bundle bundle, List<String> ids) {}
}
