package care.smith.fts.api.cda;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class DataSelectorTest {

  @Test
  void deserialization() throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper(new YAMLFactory());

    om.readValue(
        """
        ---
        """,
        DataSelector.Config.class);
  }

  @Test
  void testInstantiateConfig() {
    var filter = new DataSelector.AdditionalFilterConfig(null, null, null);
    assertThat(new DataSelector.Config(true, filter)).isNotNull();
  }
}
