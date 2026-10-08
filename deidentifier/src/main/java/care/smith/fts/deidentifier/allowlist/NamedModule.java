package care.smith.fts.deidentifier.allowlist;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

import care.smith.fts.deidentifier.Registry.Registration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * A module as the profile writes it: the handlers still carry the names the profile gave them,
 * which a {@link Module} does not keep, so that errors across modules can name them.
 *
 * @param base the paths this module keeps, with the HAPI class of the element at each
 */
record NamedModule(
    FhirPathPattern pattern,
    Map<String, Class<?>> base,
    Map<String, List<Named>> pathHandlers,
    Map<Class<?>, List<Named>> typeHandlers) {

  record Named(String name, Registration registration) {}

  Module toModule() {
    return new Module(
        pattern,
        base.keySet(),
        pathHandlers.entrySet().stream()
            .collect(toUnmodifiableMap(Map.Entry::getKey, e -> registrations(e.getValue()))),
        typeHandlers.entrySet().stream()
            .collect(toUnmodifiableMap(Map.Entry::getKey, e -> registrations(e.getValue()))));
  }

  private static List<Registration> registrations(List<Named> handlers) {
    return handlers.stream().map(Named::registration).toList();
  }

  /**
   * A terminal handler removes the value, so a handler after it silently does nothing. At runtime
   * an element meets the type handlers of every module that matches the resource, then the path
   * handlers of the modules that keep its path. All modules of one resource type can match the same
   * resource, in no defined order, so they are checked together: on every kept path a terminal
   * handler has to be the only terminal there, and either the only handler of its path or the only
   * handler there at all.
   */
  static void requireTerminalsLast(List<NamedModule> modules) {
    modules.stream()
        .collect(groupingBy(module -> module.pattern().resourceType()))
        .values()
        .forEach(NamedModule::requireTerminalsLastOfOneType);
  }

  private static void requireTerminalsLastOfOneType(List<NamedModule> modules) {
    modules.stream()
        .flatMap(module -> module.base().entrySet().stream())
        .collect(toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new))
        .forEach((path, type) -> requireTerminalLast(path, handlersAt(modules, path, type)));
  }

  private static Handlers handlersAt(List<NamedModule> modules, String path, Class<?> type) {
    return new Handlers(
        modules.stream()
            .flatMap(module -> module.typeHandlers().getOrDefault(type, List.of()).stream())
            .toList(),
        modules.stream()
            .flatMap(module -> module.pathHandlers().getOrDefault(path, List.of()).stream())
            .toList());
  }

  private record Handlers(List<Named> byType, List<Named> byPath) {
    List<Named> chain() {
      return Stream.concat(byType.stream(), byPath.stream()).toList();
    }
  }

  private static void requireTerminalLast(String path, Handlers handlers) {
    List<Named> chain = handlers.chain();
    List<Named> terminals =
        chain.stream().filter(handler -> handler.registration().terminal()).toList();
    if (!terminals.isEmpty()
        && !(terminals.size() == 1
            && (handlers.byPath().equals(terminals) || chain.equals(terminals)))) {
      throw new IllegalStateException(
          "Handler '%s' has to run last on %s, but the handlers there are %s!"
              .formatted(
                  terminals.getFirst().name(),
                  path,
                  chain.stream().map(Named::name).collect(joining(", "))));
    }
  }
}
