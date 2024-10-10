package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PseudonymizeRequestTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var request =
        new PseudonymizeRequest(
            "patient123", Set.of("id1", "id2"), "example.com", Duration.ofDays(30));

    String jsonString = objectMapper.writeValueAsString(request);

    assertThat(jsonString)
        .contains("patient123")
        .contains("id1")
        .contains("id2")
        .contains("example.com")
        .contains("2592000"); // 30 Days in seconds
  }

  @Test
  void deserialize() throws JsonProcessingException {
    String json =
        """
            {
                "patientId": "patient123",
                "ids": ["id1", "id2"],
                "domain": "example.com",
                "maxDateShift": "P30D"
            }
            """;

    PseudonymizeRequest request = objectMapper.readValue(json, PseudonymizeRequest.class);

    assertThat(request.patientId()).isEqualTo("patient123");
    assertThat(request.ids()).containsExactlyInAnyOrder("id1", "id2");
    assertThat(request.domain()).isEqualTo("example.com");
    assertThat(request.maxDateShift()).isEqualTo(Duration.ofDays(30));
  }
}
