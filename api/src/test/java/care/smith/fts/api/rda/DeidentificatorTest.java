package care.smith.fts.api.rda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class DeidentificatorTest {

  @Test
  void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    om.readValue(
        """
        ---
        """,
        Deidentificator.Config.class);
  }

  @Test
  void testInstantiateConfig() {
    assertThat(new Deidentificator.Config()).isNotNull();
  }
}
