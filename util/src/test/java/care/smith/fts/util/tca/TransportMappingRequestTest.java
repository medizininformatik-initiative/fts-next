package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.DateShiftPreserve;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TransportMappingRequestTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var request =
        new TransportMappingRequest(
            "patient123",
            "patientIdentifierSystem",
            Map.of("patient123.Patient:id1", "tId1", "patient123.Patient:id2", "tId2"),
            Map.of("tId3", "2024-03-15", "tId4", "2024-01-01"),
            new TcaDomains("pDomain", "sDomain", "dDomain"),
            Duration.ofDays(30),
            DateShiftPreserve.NONE);

    String jsonString = objectMapper.writeValueAsString(request);

    assertThat(jsonString)
        .contains("patient123")
        .contains("patientIdentifierSystem")
        .contains("patient123.Patient:id1")
        .contains("tId1")
        .contains("patient123.Patient:id2")
        .contains("tId2")
        .contains("tId3")
        .contains("2024-03-15")
        .contains("tId4")
        .contains("2024-01-01")
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
          "patientIdentifier": "patient123",
          "patientIdentifierSystem": "patientIdentifierSystem",
          "idMappings": {"patient123.Patient:id1": "tId1", "patient123.Patient:id2": "tId2"},
          "dateMappings": {"tId3": "2024-03-15", "tId4": "2024-01-01"},
          "tcaDomains": {
            "pseudonym" : "pDomain",
            "salt" : "sDomain",
            "dateShift" : "dDomain"
          },
          "maxDateShift": "P30D"
        }
        """;

    TransportMappingRequest request = objectMapper.readValue(json, TransportMappingRequest.class);

    assertThat(request.patientIdentifier()).isEqualTo("patient123");
    assertThat(request.idMappings())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("patient123.Patient:id1", "tId1", "patient123.Patient:id2", "tId2"));
    assertThat(request.dateMappings())
        .containsExactlyInAnyOrderEntriesOf(Map.of("tId3", "2024-03-15", "tId4", "2024-01-01"));
    assertThat(request.tcaDomains().pseudonym()).isEqualTo("pDomain");
    assertThat(request.tcaDomains().salt()).isEqualTo("sDomain");
    assertThat(request.tcaDomains().dateShift()).isEqualTo("dDomain");
    assertThat(request.maxDateShift()).isEqualTo(Duration.ofDays(30));
  }
}
