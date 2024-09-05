package care.smith.fts.cda.impl;

import care.smith.fts.cda.services.FhirResolveConfig;
import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;

public record EverythingDataSelectorConfig(
    /* Server to fetch data from */
    @NotNull HttpClientConfig fhirServer,

    /* */
    FhirResolveConfig resolve) {

  public EverythingDataSelectorConfig(HttpClientConfig fhirServer) {
    this(fhirServer, null);
  }
}
