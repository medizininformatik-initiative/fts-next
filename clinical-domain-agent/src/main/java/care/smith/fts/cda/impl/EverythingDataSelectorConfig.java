package care.smith.fts.cda.impl;

import care.smith.fts.cda.services.FhirResolveConfig;
import care.smith.fts.util.HTTPClientConfig;
import jakarta.validation.constraints.NotNull;

public record EverythingDataSelectorConfig(
    /* Server to fetch data from */
    @NotNull HTTPClientConfig fhirServer,

    /* */
    FhirResolveConfig resolve) {

  public EverythingDataSelectorConfig(HTTPClientConfig fhirServer) {
    this(fhirServer, null);
  }
}
