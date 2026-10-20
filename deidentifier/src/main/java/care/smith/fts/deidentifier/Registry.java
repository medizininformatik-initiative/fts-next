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
   * @param name the name a profile uses for the handler; for an alias, the alias
   * @param valueType the handler accepts elements of this type and its subtypes
   * @param handler the handler, typed for the engine
   * @param terminal whether no other handler may run after this one on the same element
   */
  public record Registration(
      String name, Class<?> valueType, DeidentifierHandler<Object> handler, boolean terminal) {

    Registration withName(String name) {
      return new Registration(name, valueType, handler, terminal);
    }
  }

  private final Map<String, Registration> registrations = new HashMap<>();

  public <T> void addHandler(String name, Class<T> valueType, DeidentifierHandler<T> handler) {
    register(registration(name, valueType, handler, false));
  }

  /**
   * Registers a handler that has to run last on its element, e.g. one that removes the value and
   * leaves nothing for a handler after it.
   */
  public <T> void addTerminalHandler(
      String name, Class<T> valueType, DeidentifierHandler<T> handler) {
    register(registration(name, valueType, handler, true));
  }

  private void register(Registration registration) {
    var name = requireNonNull(registration.name());
    if (registrations.putIfAbsent(name, registration) != null) {
      throw new IllegalStateException(
          "There is already a handler named %s registered!".formatted(name));
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> Registration registration(
      String name, Class<T> valueType, DeidentifierHandler<T> handler, boolean terminal) {
    requireNonNull(valueType);
    requireNonNull(handler);
    return new Registration(name, valueType, (DeidentifierHandler<Object>) handler, terminal);
  }

  /** Registers a second name for a handler that is already registered under {@code name}. */
  public void addAlias(String alias, String name) {
    register(resolve(name).withName(alias));
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
