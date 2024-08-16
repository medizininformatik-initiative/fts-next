package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

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
    Map<String, String> idMap = Map.of("original", "pseudonym");
    var response = new PseudonymizeResponse("tIDMapName", idMap, Duration.ofDays(14));

    assertThat(objectMapper.writeValueAsString(response))
        .contains("tIDMapName")
        .contains("original")
        .contains("pseudonym")
        .contains("1209600");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    var response =
        objectMapper.readValue(
            """
                    {"tIDMapName": "tIDMapName", "originalToTransportIDMap":{"original":"pseudonym"},"dateShiftValue":1209600.000000000}
                    """,
            PseudonymizeResponse.class);

    assertThat(response.dateShiftValue()).isEqualTo(Duration.ofDays(14));
    assertThat(response.originalToTransportIDMap()).isEqualTo(Map.of("original", "pseudonym"));
  }
}
