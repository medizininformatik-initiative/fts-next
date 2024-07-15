package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.tca.deidentification.GpasParameterResponse.Parameter;
import care.smith.fts.tca.deidentification.GpasParameterResponse.Parameter.Part.ValueIdentifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GpasParameterResponseTest {
  private GpasParameterResponse gpasParameterResponse;

  @BeforeEach
  public void setup() {
    ValueIdentifier valueIdentifier1 = new ValueIdentifier("123");
    ValueIdentifier valueIdentifier2 = new ValueIdentifier("456");

    Parameter.Part part1 = new Parameter.Part("original", valueIdentifier1);
    Parameter.Part part2 = new Parameter.Part("pseudonym", valueIdentifier2);

    Parameter parameter1 = new Parameter("param1", List.of(part1, part2));
    Parameter parameter2 = new Parameter("param2", List.of(part1, part2));

    gpasParameterResponse =
        new GpasParameterResponse("resourceType", List.of(parameter1, parameter2));
  }

  @Test
  void getMappedID() {
    Map<String, String> expectedMap = Map.of("123", "456");

    Map<String, String> mappedID = gpasParameterResponse.getMappedID();
    assertThat(expectedMap).isEqualTo(mappedID);
  }
}
