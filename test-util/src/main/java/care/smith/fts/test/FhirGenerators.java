package care.smith.fts.test;

import static java.util.Map.entry;
import static java.util.UUID.randomUUID;

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
        "patient.json",
        Map.ofEntries(
            entry("$PATIENT_ID", patientId),
            entry("$IDENTIFIER_SYSTEM", identifierSystem),
            entry("$YEAR", year)));
  }

  static FhirGenerator<Bundle> gicsResponse(Supplier<String> patientId) throws IOException {
    return gicsResponse(randomUuid(), patientId);
  }

  static FhirGenerator<Bundle> gicsResponse(
      Supplier<String> questionnaireResponseId, Supplier<String> patientId) throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "gics-consent-response.json",
        Map.ofEntries(
            entry("$QUESTIONNAIRE_RESPONSE_ID", questionnaireResponseId),
            entry("$PATIENT_ID", patientId)));
  }

  static FhirGenerator<Bundle> resolveSearchResponse(
      Supplier<String> patientId, Supplier<String> hdsId) throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "resolve-search-response.json",
        Map.ofEntries(entry("$PATIENT_ID", patientId), entry("$HDS_ID", hdsId)));
  }

  static FhirGenerator<Bundle> transportBundle() throws IOException {
    return new FhirGenerator<>(Bundle.class, "transport-bundle.json", Map.of());
  }

  static FhirGenerator<Parameters> gpasGetOrCreateResponse(
      Supplier<String> original, Supplier<String> pseudonym) throws IOException {
    return new FhirGenerator<>(
        Parameters.class,
        "gpas-get-or-create-response.json",
        Map.ofEntries(entry("$ORIGINAL", original), entry("$PSEUDONYM", pseudonym)));
  }

  static Supplier<String> randomUuid() {
    return () -> randomUUID().toString();
  }

  static Supplier<String> withPrefix(String prefix, int start) {
    return new Supplier<>() {
      int i = start;

      @Override
      public String get() {
        return prefix + i++;
      }
    };
  }

  static Supplier<String> withPrefix(String prefix) {
    return withPrefix(prefix, 0);
  }
}
