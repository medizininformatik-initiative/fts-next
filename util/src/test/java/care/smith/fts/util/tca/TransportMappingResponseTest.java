package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransportMappingResponseTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    Map<String, String> idMap = Map.of("original", "pseudonym");
    Map<String, String> dateMap = Map.of("2024-03-15", "2024-03-20");
    var response = new TransportMappingResponse("transferId", idMap, dateMap);

    assertThat(objectMapper.writeValueAsString(response))
        .contains("transferId")
        .contains("original")
        .contains("pseudonym")
        .contains("2024-03-15")
        .contains("2024-03-20");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    var response =
        objectMapper.readValue(
            """
            {
              "transferId": "transferId",
              "transportMapping": {"original":"pseudonym"},
              "dateShiftMapping": {"2024-03-15":"2024-03-20"}
            }
            """,
            TransportMappingResponse.class);

    assertThat(response.dateShiftMapping()).isEqualTo(Map.of("2024-03-15", "2024-03-20"));
    assertThat(response.transportMapping()).isEqualTo(Map.of("original", "pseudonym"));
  }
}
