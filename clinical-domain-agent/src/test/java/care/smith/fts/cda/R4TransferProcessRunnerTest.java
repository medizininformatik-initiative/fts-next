package care.smith.fts.cda;

import static java.lang.Boolean.TRUE;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class R4TransferProcessRunnerTest {

  private static final String PATIENT_ID = "patient-150622";

  @Autowired TransferProcessFactory<Bundle> factory;

  private R4TransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new R4TransferProcessRunner(commonPool());
  }

  @Test
  void runMockTestSuccessfully() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of("pids", List.of(PATIENT_ID))),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of("deidentify", false)),
            Map.of("mock", Map.of("expect", List.of(PATIENT_ID))));

    assertThat(runner.run(factory.create(processDefinition, "test"))).allMatch(TRUE::equals);
  }
}
