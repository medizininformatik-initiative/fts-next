package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConsentFetchAllRequestTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var request =
        new ConsentFetchAllRequest("domain", Set.of("policy1", "policy2"), "policySystem");

    String jsonString = objectMapper.writeValueAsString(request);

    assertThat(jsonString)
        .contains("domain")
        .contains("policy1")
        .contains("policy2")
        .contains("policySystem");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    String json =
        """
        {
          "domain": "domain",
          "policies": ["policy1", "policy2"],
          "policySystem": "policySystem"
        }
        """;

    ConsentFetchAllRequest request = objectMapper.readValue(json, ConsentFetchAllRequest.class);

    assertThat(request.domain()).isEqualTo("domain");
    assertThat(request.policies()).containsExactlyInAnyOrder("policy1", "policy2");
    assertThat(request.policySystem()).isEqualTo("policySystem");
  }
}
