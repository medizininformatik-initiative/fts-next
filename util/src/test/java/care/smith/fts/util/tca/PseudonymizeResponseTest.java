package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PseudonymizeResponseTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    IDMap idMap = new IDMap();
    idMap.put("original", "pseudonym");
    var response = new PseudonymizeResponse(idMap, Duration.ofDays(14));

    assertThat(objectMapper.writeValueAsString(response))
        .contains("original")
        .contains("pseudonym")
        .contains("1209600");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    var response =
        objectMapper.readValue(
            """
                    {"idMap":{"original":"pseudonym"},"dateShiftValue":1209600.000000000}
                    """,
            PseudonymizeResponse.class);

    assertThat(response.dateShiftValue()).isEqualTo(Duration.ofDays(14));
    assertThat(response.idMap()).isEqualTo(Map.of("original", "pseudonym"));
  }
}
