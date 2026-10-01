package care.smith.fts.deidentifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class RegistryTest {

  private static final DeidentifierHandler<StringType> NOOP =
      (value, context) -> Optional.of(value);

  @Test
  void resolveReturnsTheRegisteredHandlerWithTheTypeItWorksOn() {
    Registry registry = new Registry();
    registry.addHandler("noop", StringType.class, NOOP);

    Registry.Registration registration = registry.resolve("noop");

    assertThat(registration.handler()).isSameAs(NOOP);
    assertThat(registration.valueType()).isEqualTo(StringType.class);
  }

  @Test
  void aTerminalHandlerIsRegisteredAsTerminal() {
    Registry registry = new Registry();
    registry.addTerminalHandler("last", StringType.class, NOOP);
    registry.addHandler("any", StringType.class, NOOP);

    assertThat(registry.resolve("last").terminal()).isTrue();
    assertThat(registry.resolve("any").terminal()).isFalse();
  }

  @Test
  void anAliasResolvesToTheSameHandler() {
    Registry registry = new Registry();
    registry.addHandler("canonical", StringType.class, NOOP);
    registry.addAlias("older-name", "canonical");

    assertThat(registry.resolve("older-name")).isSameAs(registry.resolve("canonical"));
  }

  static Stream<Arguments> invalidUsesOfAName() {
    return Stream.of(
        Arguments.of("resolve an unknown name", (Consumer<Registry>) r -> r.resolve("the-name")),
        Arguments.of(
            "alias for an unknown name",
            (Consumer<Registry>) r -> r.addAlias("older-name", "the-name")),
        Arguments.of(
            "second handler under the same name",
            (Consumer<Registry>)
                r -> {
                  r.addHandler("the-name", StringType.class, NOOP);
                  r.addHandler("the-name", StringType.class, NOOP);
                }));
  }

  /** The error names the handler, so the profile author finds the entry. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidUsesOfAName")
  void rejectsAnInvalidUseOfANameWithTheNameInTheError(String use, Consumer<Registry> action) {
    assertThatThrownBy(() -> action.accept(new Registry()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("the-name");
  }
}
