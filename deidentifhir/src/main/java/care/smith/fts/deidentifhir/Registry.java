package care.smith.fts.deidentifhir;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.StringType;

/**
 * Named handlers that a HOCON config can reference in its {@code paths} and {@code types} sections.
 */
public class Registry {

  /**
   * The name whose handler doubles as the reference rule of the bundle skeleton: {@code
   * Bundle.entry.fullUrl} and {@code Bundle.entry.request.url} name a resource the same way a
   * reference does, so all of them have to be replaced by the same rule.
   */
  public static final String REFERENCE_HANDLER_NAME = "referenceReplacementHandler";

  private final Map<String, DeidentifhirHandler<?>> handlers = new HashMap<>();

  public void addHandler(String name, DeidentifhirHandler<?> handler) {
    requireNonNull(name);
    requireNonNull(handler);
    if (handlers.putIfAbsent(name, handler) != null) {
      throw new IllegalStateException(
          "There is already a handler named %s registered!".formatted(name));
    }
  }

  /** The handler registered under {@link #REFERENCE_HANDLER_NAME}, typed for the engine. */
  @SuppressWarnings("unchecked")
  public Optional<DeidentifhirHandler<StringType>> referenceHandler() {
    return getHandler(REFERENCE_HANDLER_NAME).map(h -> (DeidentifhirHandler<StringType>) h);
  }

  public Optional<DeidentifhirHandler<?>> getHandler(String name) {
    return Optional.ofNullable(handlers.get(name));
  }

  /** Registers a second name for a handler that is already registered under {@code name}. */
  public void addAlias(String alias, String name) {
    addHandler(alias, resolve(name));
  }

  /**
   * @return the handler registered under {@code name}
   * @throws IllegalStateException when no handler of that name is registered
   */
  public DeidentifhirHandler<?> resolve(String name) {
    return getHandler(name)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Cannot resolve requested handler '%s'!".formatted(name)));
  }
}
