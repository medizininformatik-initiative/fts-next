package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class TransportMappingResponseTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serialize() throws JacksonException {
    var response = new TransportMappingResponse("transferId");

    assertThat(objectMapper.writeValueAsString(response)).contains("transferId");
  }

  @Test
  void deserialize() throws JacksonException {
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
