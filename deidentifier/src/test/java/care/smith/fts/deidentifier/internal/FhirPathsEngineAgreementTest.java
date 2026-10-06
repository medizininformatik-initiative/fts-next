package care.smith.fts.deidentifier.internal;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.deidentifier.Deidentifier;
import care.smith.fts.deidentifier.Rule;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link FhirPaths} and the engine spell paths independently. Every path the engine visits must
 * resolve to the class of the value found there, or the profile checks reject valid paths.
 */
class FhirPathsEngineAgreementTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"resourceType": "Patient", "id": "p1",
         "meta": {"lastUpdated": "2024-01-01T00:00:00Z", "profile": ["https://example.org/p"]},
         "extension": [{"url": "https://example.org/e", "extension": [
           {"url": "inner", "valueUri": "https://example.org/u"},
           {"url": "coded", "valueCoding": {"system": "https://example.org/s", "code": "c"}}]}],
         "identifier": [{"system": "https://example.org/id", "value": "42",
           "type": {"coding": [{"system": "https://example.org/t", "code": "MR"}]}}],
         "name": [{"family": "Doe", "given": ["Jane"]}],
         "gender": "other", "birthDate": "1970-01-01", "deceasedDateTime": "2020-01-01",
         "address": [{"postalCode": "12345", "country": "DE"}],
         "contact": [{"name": {"family": "Roe"}, "gender": "male"}]}
        """,
        """
        {"resourceType": "Observation", "status": "final",
         "code": {"coding": [{"system": "http://loinc.org", "code": "1-8"}], "text": "t"},
         "subject": {"reference": "Patient/p1"},
         "effectiveDateTime": "2024-01-01T10:00:00+01:00",
         "valueQuantity": {"value": 1.5, "unit": "mg", "system": "http://unitsofmeasure.org"},
         "component": [{"code": {"text": "c"},
           "valueCodeableConcept": {"coding": [{"code": "x"}]}}]}
        """,
        """
        {"resourceType": "Encounter", "status": "finished",
         "class": {"system": "https://example.org/c", "code": "IMP"},
         "period": {"start": "2024-01-01", "end": "2024-01-02"},
         "diagnosis": [{"condition": {"reference": "Condition/c1"}, "rank": 1}]}
        """,
        """
        {"resourceType": "Medication",
         "ingredient": [{"itemCodeableConcept": {"text": "i"}, "isActive": true,
           "strength": {"numerator": {"value": 1}, "denominator": {"value": 2}}}]}
        """
      })
  void resolvesEveryPathTheEngineVisitsToTheClassOfItsValue(String json) {
    Resource resource = (Resource) FhirContext.forR4Cached().newJsonParser().parseResource(json);
    Map<String, Class<?>> visited = new HashMap<>();
    new Deidentifier(
            r ->
                (path, element) -> {
                  visited.put(String.join(".", path), element.getClass());
                  return new Rule.Apply(List.of());
                })
        .deidentify(resource);

    assertThat(visited).isNotEmpty();
    visited.forEach(
        (path, valueClass) ->
            assertThat(FhirPaths.elementType(path)).as(path).contains(valueClass));
  }
}
