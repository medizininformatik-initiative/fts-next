package care.smith.fts.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

class DataSelectorTest {

  @Test
  void deserialization() throws JacksonException {
    ObjectMapper om = YAMLMapper.builder().build();

    om.readValue(
        """
        ---
        """,
        DataSelector.Config.class);
  }

  @Test
  void testInstantiateConfig() {
    assertThat(new DataSelector.Config(true)).isNotNull();
  }
}
