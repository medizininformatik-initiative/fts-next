package care.smith.fts.cda;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class ExternalCohortSelectorE2E extends AbstractCohortSelectorE2E {

  public ExternalCohortSelectorE2E() {
    super("external-consent-example.yaml");
  }

  @Test
  void testStartTransferAllProcessWithExampleProject() {
    var testBodyValues = List.of("[\"patient-identifier-1\"]");
    for (String bodyValue : testBodyValues) {
      log.info("Testing with body value: {}", bodyValue);
      executeTransferTest(bodyValue);
    }
  }
}
