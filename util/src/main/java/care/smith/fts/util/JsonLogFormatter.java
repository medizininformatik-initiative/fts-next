package care.smith.fts.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface JsonLogFormatter {
  static <T> String asJson(ObjectMapper om, T t) {
    try {
      return om.writer().writeValueAsString(t);
    } catch (JsonProcessingException ignored) {
      return t.toString();
    }
  }
}
