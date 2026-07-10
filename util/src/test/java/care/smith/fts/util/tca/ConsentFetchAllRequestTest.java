package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class ConsentFetchAllRequestTest {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serialize() throws JacksonException {
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
  void deserialize() throws JacksonException {
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
