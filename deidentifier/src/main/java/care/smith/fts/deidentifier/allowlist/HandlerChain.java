package care.smith.fts.deidentifier.allowlist;

import static java.util.stream.Collectors.joining;

import care.smith.fts.deidentifier.Registry.Registration;
import java.util.List;
import java.util.stream.Stream;

/**
 * The handlers that run on one element, in order: the type handlers of the given modules for the
 * exact class of the element, then their path handlers for its path. Both the engine and the
 * terminal check build the chain here, so the two cannot disagree on its order.
 */
record HandlerChain(List<Registration> byType, List<Registration> byPath) {

  /**
   * The chain at {@code path} across {@code modules}. A module only has path handlers for paths in
   * its base, so only the modules that keep {@code path} contribute path handlers.
   */
  static HandlerChain at(List<Module> modules, String path, Class<?> elementClass) {
    return new HandlerChain(
        modules.stream()
            .flatMap(module -> module.typeHandlers().getOrDefault(elementClass, List.of()).stream())
            .toList(),
        modules.stream()
            .flatMap(module -> module.pathHandlers().getOrDefault(path, List.of()).stream())
            .toList());
  }

  List<Registration> all() {
    return Stream.concat(byType.stream(), byPath.stream()).toList();
  }

  /**
   * A terminal handler removes the value, so a handler after it silently does nothing. A terminal
   * handler has to be the only terminal in the chain, and either the only path handler or the only
   * handler at all.
   */
  void requireTerminalLast(String path) {
    var chain = all();
    var terminals = chain.stream().filter(Registration::terminal).toList();
    if (!terminals.isEmpty()
        && !(terminals.size() == 1 && (byPath.equals(terminals) || chain.equals(terminals)))) {
      throw new IllegalStateException(
          "Handler '%s' has to run last on %s, but the handlers there are %s!"
              .formatted(
                  terminals.getFirst().name(),
                  path,
                  chain.stream().map(Registration::name).collect(joining(", "))));
    }
  }
}
