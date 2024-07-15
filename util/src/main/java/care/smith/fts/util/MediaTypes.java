package care.smith.fts.util;

import static org.springframework.http.MediaType.parseMediaType;

import org.springframework.http.MediaType;

public abstract class MediaTypes {
  private MediaTypes() {}

  public static final String APPLICATION_FHIR_JSON_VALUE = "application/fhir+json";

  public static final MediaType APPLICATION_FHIR_JSON = parseMediaType(APPLICATION_FHIR_JSON_VALUE);
}
