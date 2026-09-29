package care.smith.fts.deidentifier;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.deidentifier.handlers.Handlers;
import care.smith.fts.deidentifier.internal.PseudonymUuid;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class PseudonymIdentityTest {

  private static final DeidentifierHandler<StringType> REFERENCE_HANDLER =
      Handlers.referenceReplacementHandler(
          (resourceType, id) -> "pseudonym-of-" + id, (system, value) -> "value-pseudonym");

  private final PseudonymIdentity identity = PseudonymIdentity.of(REFERENCE_HANDLER);

  private static HandlerContext bundleContext() {
    return HandlerContext.of(List.of(new Bundle()));
  }

  /**
   * The two ends of one link: an entry's fullUrl and a reference to it carry the same pseudonym,
   * the fullUrl as a UUID in urn form because it has to stay an absolute URI.
   */
  @Test
  void aFullUrlAndAReferenceToTheSameResourceCarryTheSamePseudonym() {
    HandlerContext context =
        bundleContext().withEntryTypes(Map.of("urn:uuid:8d1f-42", "Condition"));

    String fullUrl = identity.fullUrl("urn:uuid:8d1f-42", context).orElseThrow();
    StringType reference =
        REFERENCE_HANDLER
            .apply(
                List.of("Encounter", "diagnosis", "condition", "reference"),
                new StringType("urn:uuid:8d1f-42"),
                context)
            .orElseThrow();

    assertThat(reference.getValue()).isEqualTo("Condition/pseudonym-of-8d1f-42");
    assertThat(fullUrl).isEqualTo("urn:uuid:" + PseudonymUuid.uuidFrom("pseudonym-of-8d1f-42"));
  }

  @Test
  void aFullUrlThatNamesAServerCannotBePseudonymizedAndIsDropped() {
    assertThat(identity.fullUrl("https://server.example/fhir/Patient/123", bundleContext()))
        .isEmpty();
  }

  @Test
  void aRequestUrlThatOnlyNamesAResourceTypeStaysAsItIs() {
    assertThat(PseudonymIdentity.none().requestUrl("Patient", bundleContext())).contains("Patient");
  }

  @Test
  void aTargetedRequestUrlIsPseudonymizedLikeAReference() {
    assertThat(identity.requestUrl("Patient/123", bundleContext()))
        .contains("Patient/pseudonym-of-123");
  }

  /** The MII corpus shape: the system of a conditional url is itself a URL with slashes. */
  @Test
  void aConditionalRequestUrlWhoseSystemCarriesSlashesIsStillPseudonymized() {
    assertThat(
            identity.requestUrl(
                "Location?identifier=https://synthea.example|1fe6", bundleContext()))
        .contains("Location?identifier=https://synthea.example|value-pseudonym");
  }

  /** A search by anything but an identifier cannot be pseudonymized, so the request is dropped. */
  @Test
  void aRequestUrlWithANonIdentifierQueryIsDroppedNotCrashedOn() {
    assertThat(identity.requestUrl("Patient?name=Smith", bundleContext())).isEmpty();
  }

  /** A query whose value carries a slash must not be misread as a relative reference. */
  @Test
  void aRequestUrlWithAReferenceQueryIsDroppedNotMangled() {
    assertThat(identity.requestUrl("Observation?patient=Patient/1", bundleContext())).isEmpty();
  }

  /** A url without a resource type still carries an id or identifier and must not pass as is. */
  @Test
  void aRequestUrlWithoutResourceTypeIsDropped() {
    assertThat(identity.requestUrl("/Patient/123", bundleContext())).isEmpty();
    assertThat(identity.requestUrl("?identifier=sys|12345", bundleContext())).isEmpty();
  }

  /** Without a reference rule, nothing that names a resource can be kept. */
  @Test
  void withoutAReferenceRuleFullUrlAndTargetedRequestsAreDropped() {
    assertThat(PseudonymIdentity.none().fullUrl("urn:uuid:8d1f-42", bundleContext())).isEmpty();
    assertThat(PseudonymIdentity.none().requestUrl("Patient/123", bundleContext())).isEmpty();
  }
}
