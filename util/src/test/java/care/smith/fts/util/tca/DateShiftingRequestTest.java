package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class DateShiftingRequestTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var request = new DateShiftingRequest("id1", Duration.ofDays(7));

    String jsonString = objectMapper.writeValueAsString(request);

    assertThat(jsonString).contains("id1").contains("604800"); // 7 days in seconds
  }

  @Test
  void deserialize() throws JsonProcessingException {
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
