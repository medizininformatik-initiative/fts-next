package care.smith.fts.test;

import static care.smith.fts.test.MalformedConsentDates.MALFORMED_DATE;
import static care.smith.fts.test.MalformedConsentDates.encodeWithMalformedDates;
import static care.smith.fts.test.MalformedConsentDates.provisionWithPlaceholderStart;
import static org.assertj.core.api.Assertions.assertThat;

import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.junit.jupiter.api.Test;

class MalformedConsentDatesTest {

  private static final String POLICY_SYSTEM = "https://example.org/policy";

  @Test
  void provisionPermitsPolicy() {
    var provision = provisionWithPlaceholderStart(POLICY_SYSTEM, "MDAT_erheben");

    assertThat(provision.getType()).isEqualTo(ConsentProvisionType.PERMIT);
    assertThat(provision.getCodeFirstRep().getCodingFirstRep().getSystem())
        .isEqualTo(POLICY_SYSTEM);
    assertThat(provision.getCodeFirstRep().getCodingFirstRep().getCode()).isEqualTo("MDAT_erheben");
  }

  @Test
  void encodingReplacesPlaceholderWithMalformedDate() {
    var consent = new Consent();
    consent.getProvision().addProvision(provisionWithPlaceholderStart(POLICY_SYSTEM, "MDAT"));

    assertThat(encodeWithMalformedDates(consent))
        .contains("\"start\":\"%s\"".formatted(MALFORMED_DATE))
        .doesNotContain("1999-09-09");
  }
}
