package care.smith.fts.util;

import static care.smith.fts.util.JsonLogFormatter.asJson;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JsonLogFormatterTest {
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
  }

  @Test
  void shouldSerializeSimpleObject() {
    SerializableObject obj = new SerializableObject("test", 123);
    String result = asJson(objectMapper, obj);
    assertEquals("{\"name\":\"test\",\"value\":123}", result);
  }

  @Test
  void shouldHandleNull() {
    String result = asJson(objectMapper, null);
    assertEquals("null", result);
  }

  @Test
  void shouldFallbackToToStringOnSerializationFailure() {
    UnserializableObject obj = new UnserializableObject();
    String result = asJson(objectMapper, obj);
    assertEquals(obj.toString(), result);
  }

  private record SerializableObject(String name, int value) {}

  private static final class UnserializableObject {
    // Jackson invokes this getter during serialization; the thrown exception is wrapped as a
    // JacksonException, exercising asJson's toString fallback. A property that actively throws is
    // needed to force a serialization failure.
    public String getValue() {
      throw new IllegalStateException("cannot serialize");
    }

    @Override
    public String toString() {
      return "UnserializableObject";
    }
  }
}
