package care.smith.fts.test;

import static java.util.Map.entry;
import static java.util.UUID.randomUUID;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;

public interface FhirGenerators {

  static FhirGenerator<Bundle> patient(
      Supplier<String> patientId,
      Supplier<String> identifierSystem,
      Supplier<String> patientIdentifier,
      Supplier<String> year)
      throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "patient.json",
        Map.ofEntries(
            entry("$PATIENT_ID", patientId),
            entry("$IDENTIFIER_SYSTEM", identifierSystem),
            entry("$PATIENT_IDENTIFIER", patientIdentifier),
            entry("$YEAR", year)));
  }

  static FhirGenerator<Bundle> gicsResponse(Supplier<String> patientId) throws IOException {
    return gicsResponse(randomUuid(), patientId, () -> "patient-1");
  }

  static FhirGenerator<Bundle> gicsResponse(
      Supplier<String> questionnaireResponseId,
      Supplier<String> patientId,
      Supplier<String> patientIdentifier)
      throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "gics-consent-response.json",
        Map.ofEntries(
            entry("$QUESTIONNAIRE_RESPONSE_ID", questionnaireResponseId),
            entry("$PATIENT_ID", patientId),
            entry("$PATIENT_IDENTIFIER", patientIdentifier)));
  }

  static FhirGenerator<Bundle> resolveSearchResponse(
      Supplier<String> patientId, Supplier<String> patientIdentifier, Supplier<String> hdsId)
      throws IOException {
    return new FhirGenerator<>(
        Bundle.class,
        "resolve-search-response.json",
        Map.ofEntries(
            entry("$PATIENT_ID", patientId),
            entry("$PATIENT_IDENTIFIER", patientIdentifier),
            entry("$HDS_ID", hdsId)));
  }

  static FhirGenerator<Parameters> gpasGetOrCreateResponse(
      Supplier<String> original, Supplier<String> pseudonym) throws IOException {
    return gpasGetOrCreateResponse(original, () -> "domain", pseudonym);
  }

  static FhirGenerator<Parameters> gpasGetOrCreateResponse(
      Supplier<String> original, Supplier<String> target, Supplier<String> pseudonym)
      throws IOException {
    return new FhirGenerator<>(
        Parameters.class,
        "gpas-get-or-create-response.json",
        Map.ofEntries(
            entry("$ORIGINAL", original),
            entry("$TARGET", target),
            entry("$PSEUDONYM", pseudonym)));
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

  static <T> Supplier<T> fromList(List<T> list) {
    return new Supplier<>() {
      int i = 0;

      @Override
      public T get() {
        return list.get(i++);
      }
    };
  }
}
