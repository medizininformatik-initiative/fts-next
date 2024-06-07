package care.smith.fts.cda.services;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import jakarta.validation.constraints.NotBlank;

public record FhirResolveConfig(
    /* The system of the patient's identifier that is used to resolve a PID */
    @NotBlank String patientIdentifierSystem) {

  public FhirResolveService createService(IGenericClient client) {
    return new FhirResolveService(patientIdentifierSystem(), client);
  }
}
