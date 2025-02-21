package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.DateShiftPreserve;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransportMappingRequestTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var request =
        new TransportMappingRequest(
            "patient123",
            Set.of("id1", "id2"),
            new TCADomains("pDomain", "sDomain", "dDomain"),
            Duration.ofDays(30),
            DateShiftPreserve.NONE);

    String jsonString = objectMapper.writeValueAsString(request);

    assertThat(jsonString)
        .contains("patient123")
        .contains("id1")
        .contains("id2")
        .contains("pDomain")
        .contains("sDomain")
        .contains("dDomain")
        .contains("2592000"); // 30 Days in seconds
  }

  @Test
  void deserialize() throws JsonProcessingException {
    String json =
        """
            {
                "patientId": "patient123",
                "resourceIds": ["id1", "id2"],
                "tcaDomains": {
                  "pseudonym" : "pDomain",
                  "salt" : "sDomain",
                  "dateShift" : "dDomain"
                },
                "maxDateShift": "P30D"
            }
            """;

    TransportMappingRequest request = objectMapper.readValue(json, TransportMappingRequest.class);

    assertThat(request.patientId()).isEqualTo("patient123");
    assertThat(request.resourceIds()).containsExactlyInAnyOrder("id1", "id2");
    assertThat(request.tcaDomains().pseudonym()).isEqualTo("pDomain");
    assertThat(request.tcaDomains().salt()).isEqualTo("sDomain");
    assertThat(request.tcaDomains().dateShift()).isEqualTo("dDomain");
    assertThat(request.maxDateShift()).isEqualTo(Duration.ofDays(30));
  }
}
