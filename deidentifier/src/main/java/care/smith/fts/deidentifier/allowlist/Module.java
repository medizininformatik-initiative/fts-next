package care.smith.fts.deidentifier.allowlist;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;

import care.smith.fts.deidentifier.Registry.Registration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One module of an allow-list profile: a pattern that selects resources, the paths it keeps, and
 * the handlers that transform some of them.
 *
 * @param base the paths this module keeps
 * @param pathHandlers handlers per path; every key must be in {@code base}
 * @param typeHandlers handlers per HAPI class; they run on every kept element of exactly that
 *     class, at any path
 */
public record Module(
    FhirPathPattern pattern,
    Set<String> base,
    Map<String, List<Registration>> pathHandlers,
    Map<Class<?>, List<Registration>> typeHandlers) {

  public Module {
    requireNonNull(pattern);
    base = Set.copyOf(base);
    pathHandlers =
        pathHandlers.entrySet().stream()
            .collect(toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    typeHandlers =
        typeHandlers.entrySet().stream()
            .collect(toUnmodifiableMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    requireHandlersInBase(base, pathHandlers.keySet());
  }

  /** A path handler only transforms what the base keeps, so its path must be in the base. */
  private static void requireHandlersInBase(Set<String> base, Set<String> handlerPaths) {
    handlerPaths.stream()
        .filter(path -> !base.contains(path))
        .findFirst()
        .ifPresent(
            path -> {
              throw new IllegalStateException(
                  "trying to register a handler to the unspecified FHIR path %s!".formatted(path));
            });
  }
}
