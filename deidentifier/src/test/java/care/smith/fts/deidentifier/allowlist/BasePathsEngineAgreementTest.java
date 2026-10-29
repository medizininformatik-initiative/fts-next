package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.deidentifier.BundleEntryUrls;
import care.smith.fts.deidentifier.Deidentifier;
import care.smith.fts.deidentifier.Rule;
import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link BasePaths} and the engine decide independently which paths exist and which the engine
 * visits. Every path the engine visits must be a base path of the class of the value found there,
 * or the profile checks reject valid paths. The narrative in the fixture fails this test if the
 * engine starts to visit it while {@link BasePaths} still rejects it; it has no {@code div}, so
 * that the engine reaches {@code text.status} instead of failing on the XHTML.
 */
class BasePathsEngineAgreementTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        """
        {"resourceType": "Patient", "id": "p1",
         "text": {"status": "generated"},
         "meta": {"lastUpdated": "2024-01-01T00:00:00Z", "profile": ["https://example.org/p"]},
         "extension": [{"url": "https://example.org/e", "extension": [
           {"url": "inner", "valueUri": "https://example.org/u"},
           {"url": "coded", "valueCoding": {"system": "https://example.org/s", "code": "c"}}]}],
         "identifier": [{"system": "https://example.org/id", "value": "42",
           "type": {"coding": [{"system": "https://example.org/t", "code": "MR"}]}}],
         "name": [{"family": "Doe", "given": ["Jane"]}],
         "gender": "other", "birthDate": "1970-01-01", "deceasedDateTime": "2020-01-01",
         "_birthDate": {"extension": [{"url": "https://example.org/b", "valueCoding": {
           "system": "https://example.org/s", "code": "c", "extension": [
             {"url": "note", "valueString": "n"}]}}]},
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
  void acceptsEveryPathTheEngineVisitsAsBasePathOfTheClassOfItsValue(String json) {
    var resource = (Resource) FhirContext.forR4Cached().newJsonParser().parseResource(json);
    var visited = new HashMap<String, Class<?>>();
    new Deidentifier(
            r ->
                (path, element) -> {
                  visited.put(String.join(".", path), element.getClass());
                  return new Rule.Apply(List.of());
                },
            BundleEntryUrls.none())
        .deidentify(resource, "p");

    assertThat(visited).isNotEmpty();
    visited.forEach(
        (path, valueClass) -> assertThat(BasePaths.leafType(path)).as(path).isEqualTo(valueClass));
  }
}
