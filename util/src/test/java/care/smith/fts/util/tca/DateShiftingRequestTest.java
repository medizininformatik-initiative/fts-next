package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class DateShiftingRequestTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serialize() throws JacksonException {
    var request = new DateShiftingRequest("id1", Duration.ofDays(7));

    String jsonString = objectMapper.writeValueAsString(request);

    assertThat(jsonString).contains("id1").contains("PT168H"); // 7 days as ISO-8601 duration
  }

  @Test
  void deserialize() throws JacksonException {
    String json =
        """
        {
          "id": "id1",
          "dateShift": 604800.000000000
        }
        """;

    DateShiftingRequest request = objectMapper.readValue(json, DateShiftingRequest.class);

    assertThat(request.id()).isEqualTo("id1");
    assertThat(request.dateShift()).isEqualTo(Duration.ofDays(7));
  }
}
