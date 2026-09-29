package care.smith.fts.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class RegistryTest {

  private static final DeidentifhirHandler<StringType> NOOP =
      (path, value, context) -> Optional.of(value);

  @Test
  void resolveReturnsTheRegisteredHandlerWithTheTypeItWorksOn() {
    Registry registry = new Registry();
    registry.addHandler("noop", StringType.class, NOOP);

    Registry.Registration registration = registry.resolve("noop");

    assertThat(registration.handler()).isSameAs(NOOP);
    assertThat(registration.valueType()).isEqualTo(StringType.class);
  }

  @Test
  void resolveNamesTheMissingHandlerInItsError() {
    assertThatThrownBy(() -> new Registry().resolve("absent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("absent");
  }

  @Test
  void anAliasResolvesToTheSameHandler() {
    Registry registry = new Registry();
    registry.addHandler("canonical", StringType.class, NOOP);
    registry.addAlias("older-name", "canonical");

    assertThat(registry.resolve("older-name")).isSameAs(registry.resolve("canonical"));
  }

  @Test
  void anAliasForAnUnknownNameFailsAtRegistration() {
    assertThatThrownBy(() -> new Registry().addAlias("older-name", "absent"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("absent");
  }

  @Test
  void aSecondHandlerUnderTheSameNameIsRejected() {
    Registry registry = new Registry();
    registry.addHandler("noop", StringType.class, NOOP);

    assertThatThrownBy(() -> registry.addHandler("noop", StringType.class, NOOP))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("noop");
  }
}
