package care.smith.fts.cda.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;
import static lombok.AccessLevel.PRIVATE;

import care.smith.fts.cda.services.FhirResolveConfig;
import care.smith.fts.util.HttpClientConfig;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
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
  private int pageSize;

  @JsonCreator
  public EverythingDataSelectorConfig(
      @JsonProperty("fhirServer") @NotNull HttpClientConfig fhirServer,
      @JsonProperty("resolve") FhirResolveConfig resolve,
      @JsonProperty("pageSize") Integer pageSize) {
    this.fhirServer = requireNonNull(fhirServer, "fhirServer must not be null");
    this.resolve = resolve;
    this.pageSize = checkPageSize(pageSize);
  }

  private static int checkPageSize(Integer pageSize) {
    if (pageSize != null) {
      checkArgument(pageSize > 0, "pageSize must be greater than 0");
      return pageSize;
    } else {
      return DEFAULT_PAGE_SIZE;
    }
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
