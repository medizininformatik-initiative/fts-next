package care.smith.fts.util;

import static care.smith.fts.util.JsonLogFormatter.asJson;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    private final Object circular = this; // Creates circular reference that can't be serialized

    @Override
    public String toString() {
      return "UnserializableObject";
    }
  }
}
