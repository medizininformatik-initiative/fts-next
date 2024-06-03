package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class TransferProcessFactoryTest {

  @Autowired private ApplicationContext context;
  @Autowired private ObjectMapper objectMapper;

  TransferProcessFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TransferProcessFactory(context, objectMapper);
  }

  @Test
  void nullConfigThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              factory.create(null);
            });
  }

  @Test
  void emptyConfigThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> {
              factory.create(new TransferProcessConfig());
            });
  }

  @Test
  void ignoreCommonConfigEntries() {
    TransferProcessConfig processDefinition = new TransferProcessConfig();
    processDefinition.setCohortSelector(Map.of("mock", Map.of()));
    processDefinition.setDataSelector(
        Map.of("mock", Map.of(), "resolvePatient", Map.of(), "additionalFilter", Map.of()));
    processDefinition.setDeidentificationProvider(Map.of("mock", Map.of()));
    processDefinition.setBundleSender(Map.of("mock", Map.of()));

    assertThatNoException()
        .isThrownBy(
            () -> {
              factory.create(processDefinition);
            });
  }

  @Test
  void unknownConfigEntriesThrow() {
    TransferProcessConfig processDefinition = new TransferProcessConfig();
    processDefinition.setCohortSelector(Map.of("mock", Map.of(), "unknown", Map.of()));
    processDefinition.setDataSelector(Map.of("mock", Map.of()));
    processDefinition.setDeidentificationProvider(Map.of("mock", Map.of()));
    processDefinition.setBundleSender(Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              factory.create(processDefinition);
            });
  }

  @Test
  void noImplementationThrows() {
    TransferProcessConfig processDefinition = new TransferProcessConfig();
    processDefinition.setCohortSelector(Map.of("mock", Map.of()));
    processDefinition.setDataSelector(Map.of("resolvePatient", Map.of()));
    processDefinition.setDeidentificationProvider(Map.of("mock", Map.of()));
    processDefinition.setBundleSender(Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              factory.create(processDefinition);
            });
  }

  @Test
  void unknownImplementationThrows() {
    TransferProcessConfig processDefinition = new TransferProcessConfig();
    processDefinition.setCohortSelector(Map.of("unknown", Map.of()));
    processDefinition.setDataSelector(Map.of("mock", Map.of()));
    processDefinition.setDeidentificationProvider(Map.of("mock", Map.of()));
    processDefinition.setBundleSender(Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              factory.create(processDefinition);
            });
  }

  @Test
  void validConfig() {
    TransferProcessConfig processDefinition = new TransferProcessConfig();
    processDefinition.setCohortSelector(Map.of("mock", Map.of()));
    processDefinition.setDataSelector(Map.of("mock", Map.of()));
    processDefinition.setDeidentificationProvider(Map.of("mock", Map.of()));
    processDefinition.setBundleSender(Map.of("mock", Map.of()));

    assertThatNoException()
        .isThrownBy(
            () -> {
              factory.create(processDefinition);
            });
  }
}
