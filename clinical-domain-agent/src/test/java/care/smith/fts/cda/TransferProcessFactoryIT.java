package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class TransferProcessFactoryIT {

  @Autowired private ApplicationContext context;

  @Autowired
  @Qualifier("transferProcessObjectMapper")
  private ObjectMapper objectMapper;

  TransferProcessFactory factory;

  @BeforeEach
  void setUp() {
    factory = new TransferProcessFactory(context, objectMapper);
  }

  @Test
  void nullConfigThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> factory.create(null, "example"));
  }

  @Test
  void emptyConfigThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(
            () -> factory.create(new TransferProcessConfig(null, null, null, null), "example"));
  }

  @Test
  void commonConfigEntriesDoNotThrow() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of(), "ignoreConsent", false),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThatNoException().isThrownBy(() -> factory.create(processDefinition, "example"));
  }

  @Test
  void unknownConfigEntriesThrow() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of(), "unknown", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(processDefinition, "example"));
  }

  @Test
  void noImplementationThrows() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of()),
            Map.of("ignoreConsent", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(processDefinition, "example"))
        .withMessageContaining("No config entry looks like an implementation");
  }

  @Test
  void multipleImplementationsThrow() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of(), "mock2", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(processDefinition, "example"))
        .withMessageContaining("Multiple config entries look like an implementation");
  }

  @Test
  void unknownImplementationThrows() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("unknown", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(processDefinition, "example"))
        .withMessageContaining("Implementation unknown could not be found");
  }

  @Test
  void invalidConfigEntryThrows() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of("known", List.of("invalid"))),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> factory.create(processDefinition, "example"))
        .withMessageMatching("^Invalid \\S+ config:.*");
  }

  @Test
  void validConfig() {
    TransferProcessConfig processDefinition =
        new TransferProcessConfig(
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()),
            Map.of("mock", Map.of()));

    assertThatNoException().isThrownBy(() -> factory.create(processDefinition, "example"));
  }
}
