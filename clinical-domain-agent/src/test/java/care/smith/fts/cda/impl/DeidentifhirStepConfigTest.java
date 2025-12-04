package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.DateShiftPreserve;
import org.junit.jupiter.api.Test;

public class DeidentifhirStepConfigTest {

  @Test
  void missingDateShiftPreserveDefaultsToNone() {
    var config = new DeidentifhirStepConfig(null, null, null, null, null, null);
    assertThat(config.dateShiftPreserve()).isEqualTo(DateShiftPreserve.NONE);
  }

  @Test
  void missingUsePatientResourceIdForCompartmentDefaultsToTrue() {
    var config = new DeidentifhirStepConfig(null, null, null, null, null, null);
    assertThat(config.usePatientResourceIdForCompartment()).isTrue();
  }
}
