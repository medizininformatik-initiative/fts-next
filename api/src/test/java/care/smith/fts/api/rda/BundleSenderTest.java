package care.smith.fts.api.rda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

class BundleSenderTest {

  @Test
  void deserialization() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

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
