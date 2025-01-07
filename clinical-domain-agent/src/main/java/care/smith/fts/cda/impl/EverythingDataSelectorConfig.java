package care.smith.fts.cda.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static lombok.AccessLevel.PRIVATE;

import care.smith.fts.cda.services.FhirResolveConfig;
import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;

@Setter(PRIVATE)
@EqualsAndHashCode
@ToString
public final class EverythingDataSelectorConfig {

  public static int DEFAULT_PAGE_SIZE = 500;

  private @NotNull HttpClientConfig fhirServer;
  private FhirResolveConfig resolve;
  private int pageSize = DEFAULT_PAGE_SIZE;

  private EverythingDataSelectorConfig() {}

  public EverythingDataSelectorConfig(
      HttpClientConfig fhirServer, FhirResolveConfig resolve, int pageSize) {
    this.fhirServer = fhirServer;
    this.resolve = resolve;
    checkArgument(pageSize > 0, "pageSize must be greater than 0");
    this.pageSize = pageSize;
  }

  public @NotNull HttpClientConfig fhirServer() {
    return fhirServer;
  }

  public FhirResolveConfig resolve() {
    return resolve;
  }

  public int pageSize() {
    return pageSize;
  }
}
