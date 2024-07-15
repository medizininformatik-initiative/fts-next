package care.smith.fts.api;

import org.hl7.fhir.r4.model.Bundle;

public record ConsentedPatientBundle(Bundle bundle, ConsentedPatient consentedPatient) {}
