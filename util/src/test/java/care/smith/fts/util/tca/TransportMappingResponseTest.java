package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class TransportMappingResponseTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var response = new TransportMappingResponse("transferId");

    assertThat(objectMapper.writeValueAsString(response)).contains("transferId");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    var response =
        objectMapper.readValue(
            """
            {
              "transferId": "transferId"
            }
            """,
            TransportMappingResponse.class);

    assertThat(response.transferId()).isEqualTo("transferId");
  }
}
