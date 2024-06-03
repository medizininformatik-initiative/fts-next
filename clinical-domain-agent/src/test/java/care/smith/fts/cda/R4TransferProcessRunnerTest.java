package care.smith.fts.cda;

import static java.lang.Boolean.TRUE;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class R4TransferProcessRunnerTest {

  private static final String PATIENT_ID = "patient-150622";

  @Autowired TransferProcessFactory factory;

  private R4TransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new R4TransferProcessRunner(commonPool());
  }

  @Test
  void runMockTestSuccessfully() {
    TransferProcessConfig processDefinition = new TransferProcessConfig();
    processDefinition.setCohortSelector(Map.of("mock", Map.of("pids", List.of(PATIENT_ID))));
    processDefinition.setDataSelector(Map.of("mock", Map.of()));
    processDefinition.setDeidentificationProvider(Map.of("mock", Map.of("deidentify", false)));
    processDefinition.setBundleSender(Map.of("mock", Map.of("expect", List.of(PATIENT_ID))));

    assertThat(runner.run(factory.create(processDefinition))).allMatch(TRUE::equals);
  }
}
