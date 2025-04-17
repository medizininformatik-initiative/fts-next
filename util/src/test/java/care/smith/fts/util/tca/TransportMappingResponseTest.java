package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransportMappingResponseTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    Map<String, String> idMap = Map.of("original", "pseudonym");
    var response = new TransportMappingResponse("transferId", idMap, Duration.ofDays(14));

    assertThat(objectMapper.writeValueAsString(response))
        .contains("transferId")
        .contains("original")
        .contains("pseudonym")
        .contains("1209600");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    var response =
        objectMapper.readValue(
            """
            {
              "transferId": "transferId",
              "transportMapping": {"original":"pseudonym"},
              "dateShiftValue": 1209600.000000000
            }
            """,
            TransportMappingResponse.class);

    assertThat(response.dateShiftValue()).isEqualTo(Duration.ofDays(14));
    assertThat(response.transportMapping()).isEqualTo(Map.of("original", "pseudonym"));
  }
}
