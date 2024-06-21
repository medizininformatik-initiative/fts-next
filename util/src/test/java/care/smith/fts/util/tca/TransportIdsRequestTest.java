package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransportIdsRequestTest {

  private static final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new JavaTimeModule());

  @Test
  void serialize() throws JsonProcessingException {
    var response = new TransportIdsRequest("domain", Set.of("tid-101428"));

    assertThat(objectMapper.writeValueAsString(response)).contains("domain").contains("tid-101428");
  }

  @Test
  void deserialize() throws JsonProcessingException {
    var response =
        objectMapper.readValue(
            """
                    {"domain":"domain","ids":["tid-101428"]}
                    """,
            TransportIdsRequest.class);

    assertThat(response.domain()).isEqualTo("domain");
    assertThat(response.ids()).containsExactlyInAnyOrder("tid-101428");
  }
}
