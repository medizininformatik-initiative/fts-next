package care.smith.fts.util;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

public interface TransferProcessObjectMapper {
  static ObjectMapper create() {
    // Config-binding semantics: tolerate omitted primitive fields (e.g. boolean flags) and reject
    // unknown properties so config typos fail fast.
    return YAMLMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();
  }
}
