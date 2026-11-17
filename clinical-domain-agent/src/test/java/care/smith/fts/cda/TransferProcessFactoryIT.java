package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
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
  void commonConfigKeysDoNotThrow() {
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

  @Test
  void deprecatedDeidentifhirKeyLoadsAndWarns(CapturedOutput output) throws URISyntaxException {
    var processDefinition =
        withDeidentificator(Map.of("deidentifhir", deidentifierConfig("deidentifierConfig")));

    assertThatNoException().isThrownBy(() -> factory.create(processDefinition, "example"));
    assertThat(output).contains("deprecated", "deidentifhir", "deidentifier");
  }

  @Test
  void deprecatedDeidentifhirConfigFieldLoads() throws URISyntaxException {
    var processDefinition =
        withDeidentificator(Map.of("deidentifier", deidentifierConfig("deidentifhirConfig")));

    assertThatNoException().isThrownBy(() -> factory.create(processDefinition, "example"));
  }

  private static TransferProcessConfig withDeidentificator(Map<String, ?> deidentificator) {
    return new TransferProcessConfig(
        Map.of("mock", Map.of()),
        Map.of("mock", Map.of()),
        deidentificator,
        Map.of("mock", Map.of()));
  }

  private static Map<String, ?> deidentifierConfig(String profileKey) throws URISyntaxException {
    var profile =
        Path.of(
            TransferProcessFactoryIT.class
                .getResource("/care/smith/fts/cda/services/deidentifier/CDtoTransport.profile")
                .toURI());
    return Map.of(
        "trustCenterAgent",
        Map.of(
            "server",
            Map.of("baseUrl", "http://tc-agent:8080"),
            "domains",
            Map.of("pseudonym", "domain", "salt", "domain", "dateShift", "domain")),
        "maxDateShift",
        "P14D",
        profileKey,
        profile.toString());
  }
}
