package care.smith.fts.api.rda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class BundleSenderTest {

  @Test
  void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    om.readValue(
        """
      ---
      """,
        BundleSender.Config.class);
  }

  @Test
  void testInstantiateConfig() {
    assertThat(new BundleSender.Config()).isNotNull();
  }

  @Test
  void testInstantiateResult() {
    assertThat(new BundleSender.Result()).isNotNull();
  }
}
