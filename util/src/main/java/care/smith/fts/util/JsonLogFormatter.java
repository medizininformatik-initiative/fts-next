package care.smith.fts.util;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public interface JsonLogFormatter {
  static <T> String asJson(ObjectMapper om, T t) {
    try {
      return om.writer().writeValueAsString(t);
    } catch (JacksonException ignored) {
      return t.toString();
    }
  }
}
