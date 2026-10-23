package care.smith.fts.test;

import static care.smith.fts.util.fhir.FhirUtils.fhirResourceToString;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Consent.ProvisionComponent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;

/**
 * Builds consent responses with a malformed date. HAPI cannot hold a malformed date in its model,
 * so a provision starts at a valid placeholder, which encoding replaces with the malformed date.
 */
public interface MalformedConsentDates {

  /** No FHIR date: FHIR requires {@code -} as separator. */
  String MALFORMED_DATE = "2024/02/23";

  String PLACEHOLDER = "1999-09-09";

  static ProvisionComponent provisionWithPlaceholderStart(String policySystem, String policy) {
    return new ProvisionComponent()
        .setType(ConsentProvisionType.PERMIT)
        .setPeriod(new Period().setStartElement(new DateTimeType(PLACEHOLDER)))
        .addCode(
            new CodeableConcept().addCoding(new Coding().setSystem(policySystem).setCode(policy)));
  }

  static String encodeWithMalformedDates(Resource resource) {
    return fhirResourceToString(resource).replace(PLACEHOLDER, MALFORMED_DATE);
  }
}
