package care.smith.fts.util;

import static org.springframework.http.MediaType.parseMediaType;

import org.springframework.http.MediaType;

public interface MediaTypes {
  String APPLICATION_FHIR_JSON_VALUE = "application/fhir+json";

  MediaType APPLICATION_FHIR_JSON = parseMediaType(APPLICATION_FHIR_JSON_VALUE);
}
