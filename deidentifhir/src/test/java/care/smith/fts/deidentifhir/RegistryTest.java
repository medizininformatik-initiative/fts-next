package care.smith.fts.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegistryTest {

  private static final DeidentifhirHandler<Object> NOOP =
      (path, value, context) -> Optional.of(value);

  @Test
  void resolveReturnsTheRegisteredHandler() {
    Registry registry = new Registry();
    registry.addHandler("noop", NOOP);

    assertThat(registry.resolve("noop")).isSameAs(NOOP);
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
    registry.addHandler("canonical", NOOP);
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
    registry.addHandler("noop", NOOP);

    assertThatThrownBy(() -> registry.addHandler("noop", NOOP))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("noop");
  }
}
