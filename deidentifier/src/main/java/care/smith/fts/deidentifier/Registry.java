package care.smith.fts.deidentifier;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Named handlers that a profile can reference by name. Each handler is registered with the FHIR
 * type it works on.
 */
public class Registry {

  /**
   * A handler and the FHIR type it works on.
   *
   * @param valueType the handler accepts elements of this type and its subtypes
   * @param handler the handler, typed for the engine
   * @param terminal whether no other handler may run after this one on the same element
   */
  public record Registration(
      Class<?> valueType, DeidentifierHandler<Object> handler, boolean terminal) {}

  private final Map<String, Registration> registrations = new HashMap<>();

  public <T> void addHandler(String name, Class<T> valueType, DeidentifierHandler<T> handler) {
    register(name, registration(valueType, handler, false));
  }

  /**
   * Registers a handler that has to run last on its element, e.g. one that removes the value and
   * leaves nothing for a handler after it.
   */
  public <T> void addTerminalHandler(
      String name, Class<T> valueType, DeidentifierHandler<T> handler) {
    register(name, registration(valueType, handler, true));
  }

  private void register(String name, Registration registration) {
    requireNonNull(name);
    if (registrations.putIfAbsent(name, registration) != null) {
      throw new IllegalStateException(
          "There is already a handler named %s registered!".formatted(name));
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Registration registration(
      Class<T> valueType, DeidentifierHandler<T> handler, boolean terminal) {
    requireNonNull(valueType);
    requireNonNull(handler);
    return new Registration(valueType, (DeidentifierHandler<Object>) handler, terminal);
  }

  /** Registers a second name for a handler that is already registered under {@code name}. */
  public void addAlias(String alias, String name) {
    register(alias, resolve(name));
  }

  /**
   * @return the registration under {@code name}
   * @throws IllegalStateException when no handler of that name is registered
   */
  public Registration resolve(String name) {
    return Optional.ofNullable(registrations.get(name))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Cannot resolve requested handler '%s'!".formatted(name)));
  }
}
