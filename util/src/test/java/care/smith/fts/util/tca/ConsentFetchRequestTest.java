package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConsentFetchRequestTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var request =
        new ConsentFetchRequest(
            "domain",
            Set.of("policy1", "policy2"),
            "policySystem",
            "patientIdentifierSystem",
            List.of("id1"));

    String jsonString = objectMapper.writeValueAsString(request);

    assertThat(jsonString)
        .contains("domain")
        .contains("policy1")
        .contains("policy2")
        .contains("policySystem")
        .contains("patientIdentifierSystem")
        .contains("id1");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    String json =
        """
        {
          "domain": "domain",
          "policies": ["policy1", "policy2"],
          "policySystem": "policySystem",
          "patientIdentifierSystem": "patientIdentifierSystem",
          "pids": ["id1"]
        }
        """;

    ConsentFetchRequest request = objectMapper.readValue(json, ConsentFetchRequest.class);

    assertThat(request.domain()).isEqualTo("domain");
    assertThat(request.policies()).containsExactlyInAnyOrder("policy1", "policy2");
    assertThat(request.policySystem()).isEqualTo("policySystem");
    assertThat(request.patientIdentifierSystem()).isEqualTo("patientIdentifierSystem");
    assertThat(request.pids()).containsExactlyInAnyOrder("id1");
  }
}
