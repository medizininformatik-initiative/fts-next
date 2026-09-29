package care.smith.fts.deidentifhir;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.StringType;

/**
 * Named handlers that a HOCON config can reference in its {@code paths} and {@code types} sections.
 * Each handler is registered with the FHIR type it works on, so a profile that points it at an
 * element of another type is rejected when it is parsed, not when the first resource reaches it.
 */
public class Registry {

  /**
   * The name whose handler doubles as the reference rule of the bundle skeleton: {@code
   * Bundle.entry.fullUrl} and {@code Bundle.entry.request.url} name a resource the same way a
   * reference does, so all of them have to be replaced by the same rule.
   */
  public static final String REFERENCE_HANDLER_NAME = "referenceReplacementHandler";

  /**
   * A handler and the FHIR type it works on.
   *
   * @param valueType the handler accepts elements of this type and its subtypes
   * @param handler the handler, typed for the engine
   */
  public record Registration(Class<?> valueType, DeidentifhirHandler<Object> handler) {}

  private final Map<String, Registration> registrations = new HashMap<>();

  public <T> void addHandler(String name, Class<T> valueType, DeidentifhirHandler<T> handler) {
    register(name, registration(valueType, handler));
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
      Class<T> valueType, DeidentifhirHandler<T> handler) {
    requireNonNull(valueType);
    requireNonNull(handler);
    return new Registration(valueType, (DeidentifhirHandler<Object>) handler);
  }

  /** The handler registered under {@link #REFERENCE_HANDLER_NAME}, typed for the engine. */
  @SuppressWarnings("unchecked")
  public Optional<DeidentifhirHandler<StringType>> referenceHandler() {
    return Optional.ofNullable(registrations.get(REFERENCE_HANDLER_NAME))
        .map(registration -> (DeidentifhirHandler<StringType>) (Object) registration.handler());
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
