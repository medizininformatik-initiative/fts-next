package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.api.ConsentedPatient;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public record ConsentedPatientBundle<B extends IBaseBundle>(
    B bundle, ConsentedPatient consentedPatient) {}
