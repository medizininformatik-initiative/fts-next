package care.smith.fts.cda.services;

import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import care.smith.fts.util.HTTPClientConfig;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FhirResolveConfig(

    /* The system of the patient's identifier that is used to resolve a PID */
    @NotBlank String patientIdentifierSystem,

    /* Server to use for resolving the PID */
    @NotNull HTTPClientConfig server) {

  public FhirResolveService createService(IRestfulClientFactory clientFactory) {
    return new FhirResolveService(patientIdentifierSystem(), server().createClient(clientFactory));
  }
}
