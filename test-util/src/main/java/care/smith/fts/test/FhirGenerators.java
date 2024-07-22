package care.smith.fts.test;

import static java.util.Map.entry;

import java.io.IOException;
import java.util.Map;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;

public interface FhirGenerators {

  static FhirGenerator<Bundle> patient(
      Supplier<String> patientId, Supplier<String> identifierSystem, Supplier<String> year)
      throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "PatientTemplate.json",
        Map.ofEntries(
            entry("$PATIENT_ID", patientId),
            entry("$IDENTIFIER_SYSTEM", identifierSystem),
            entry("$YEAR", year)));
  }

  static FhirGenerator<Bundle> gicsResponse(
      Supplier<String> questionnaireResponseId, Supplier<String> patientId) throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "GicsResponseTemplate.json",
        Map.ofEntries(
            entry("$QUESTIONNAIRE_RESPONSE_ID", questionnaireResponseId),
            entry("$PATIENT_ID", patientId)));
  }

  static FhirGenerator<Bundle> resolveSearchResponse(
      Supplier<String> patientId, Supplier<String> hdsId) throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "FhirResolveSearchResponseTemplate.json",
        Map.ofEntries(entry("$PATIENT_ID", patientId), entry("$HDS_ID", hdsId)));
  }

  static FhirGenerator<Bundle> transportBundle() throws IOException {
    return new FhirGenerator<>(Bundle.class, "TransportBundleTemplate.json", Map.of());
  }

  static FhirGenerator<Parameters> gpasGetOrCreateResponse(
      Supplier<String> original, Supplier<String> pseudonym) throws IOException {
    return new FhirGenerator<>(
        Parameters.class,
        "gpas-get-or-create-response.json",
        Map.ofEntries(entry("$ORIGINAL", original), entry("$PSEUDONYM", pseudonym)));
  }
}
