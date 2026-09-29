package care.smith.fts.deidentifier.allowlist;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableMap;

import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.Registry;
import care.smith.fts.deidentifier.Rule;
import care.smith.fts.deidentifier.RuleSet;
import care.smith.fts.deidentifier.internal.FhirPaths;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Resource;

/**
 * A parsed allow-list profile: the whole HOCON configuration, resolved against a {@link Registry}
 * once. An element that no matched module lists in its {@code base} is removed.
 */
public final class Profile implements RuleSet {

  /** The package the {@code types} sections name their classes in, without their package. */
  private static final String MODEL_PACKAGE = "org.hl7.fhir.r4.model.";

  /**
   * One module of the profile: a pattern that selects resources, plus its handlers. Every base path
   * is a key of {@code pathHandlers}, with an empty list when it has no handler.
   */
  private record Module(
      FhirPathPattern pattern,
      Map<String, List<Registration>> pathHandlers,
      Map<Class<?>, List<Registration>> typeHandlers) {}

  /** One {@code key { handler = name }} entry of a {@code paths} or {@code types} section. */
  private record Registration(String key, String name, Registry.Registration registration) {

    DeidentifierHandler<Object> handler() {
      return registration.handler();
    }

    boolean terminal() {
      return registration.terminal();
    }

    Registration withKey(String otherKey) {
      return new Registration(otherKey, name, registration);
    }
  }

  private final List<Module> modules;

  private Profile(List<Module> modules) {
    this.modules = List.copyOf(modules);
  }

  public static Profile parse(Config config, Registry registry) {
    requireNonNull(config);
    requireNonNull(registry);
    ConfigObject modules = config.getObject("modules");
    List<Module> parsed =
        modules.keySet().stream()
            .map(key -> parseModule(modules.toConfig().getConfig(key), registry))
            .toList();
    requireTerminalHandlersLast(parsed);
    return new Profile(parsed);
  }

  /**
   * A terminal handler has to be the last one on every element it reaches. All modules for one
   * resource type can match the same resource, and the order among them is not defined, so the
   * chain of an element is checked over all of them: the type handlers first, then the path
   * handlers. A terminal handler is last only when it is the one path handler of that element, or
   * the one handler of any kind.
   *
   * <p>A base path that names no element is skipped: the engine never visits it.
   */
  private static void requireTerminalHandlersLast(List<Module> modules) {
    modules.stream()
        .collect(groupingBy(module -> module.pattern().resourceType()))
        .values()
        .forEach(
            sameType ->
                sameType.stream()
                    .flatMap(module -> module.pathHandlers().keySet().stream())
                    .distinct()
                    .forEach(path -> requireTerminalLast(path, sameType)));
  }

  private static void requireTerminalLast(String path, List<Module> sameType) {
    List<Registration> typeChain =
        FhirPaths.elementType(path).stream()
            .flatMap(
                elementType ->
                    sameType.stream()
                        .flatMap(
                            module ->
                                module.typeHandlers().getOrDefault(elementType, List.of()).stream()))
            .toList();
    List<Registration> pathChain =
        sameType.stream()
            .flatMap(module -> module.pathHandlers().getOrDefault(path, List.of()).stream())
            .toList();
    List<Registration> chain = Stream.concat(typeChain.stream(), pathChain.stream()).toList();
    List<Registration> terminals = chain.stream().filter(Registration::terminal).toList();
    if (terminals.isEmpty()) {
      return;
    }
    Registration terminal = terminals.getFirst();
    boolean last =
        terminals.size() == 1
            && (pathChain.equals(List.of(terminal)) || chain.equals(List.of(terminal)));
    if (!last) {
      throw new IllegalStateException(
          "Handler '%s' has to run last on %s, but the handlers there are %s!"
              .formatted(
                  terminal.name(),
                  path,
                  chain.stream().map(Registration::name).collect(joining(", "))));
    }
  }

  /**
   * The rules that apply to one resource: the modules whose patterns match it, resolved once. The
   * patterns are constant per resource, so the engine asks once and queries the result for every
   * element instead of re-matching per primitive.
   */
  public static final class Rules implements ResourceRules {

    private final List<Module> matched;

    private Rules(List<Module> matched) {
      this.matched = matched;
    }

    /**
     * The merged decision for one element: the type handlers of all matched modules run before the
     * path handlers of all matched modules. An element that no matched module lists in its base is
     * removed, whatever type handlers exist for it; one that a module lists without a handler is
     * kept unchanged. The type handlers are looked up by the class of the element.
     */
    @Override
    public Rule ruleFor(List<String> path, Base element) {
      Class<?> valueType = element.getClass();
      String pathKey = String.join(".", path);
      List<List<DeidentifierHandler<Object>>> typeChains =
          chains(module -> module.typeHandlers().get(valueType));
      List<List<DeidentifierHandler<Object>>> pathChains =
          chains(module -> module.pathHandlers().get(pathKey));
      // the base list decides what is kept; a type handler only transforms a kept element
      if (pathChains.isEmpty()) {
        return Rule.REMOVE;
      }
      return new Rule.Apply(
          Stream.concat(typeChains.stream(), pathChains.stream()).flatMap(List::stream).toList());
    }

    private List<List<DeidentifierHandler<Object>>> chains(
        Function<Module, List<Registration>> lookup) {
      return matched.stream()
          .flatMap(module -> Optional.ofNullable(lookup.apply(module)).stream())
          .map(registrations -> registrations.stream().map(Registration::handler).toList())
          .toList();
    }
  }

  @Override
  public Rules rulesFor(Resource resource) {
    return new Rules(modules.stream().filter(m -> m.pattern().matches(resource)).toList());
  }

  /** The merged decision of every module whose pattern matches {@code resource}. */
  public Rule ruleFor(Resource resource, List<String> path, Base element) {
    return rulesFor(resource).ruleFor(path, element);
  }

  private static Module parseModule(Config config, Registry registry) {
    FhirPathPattern pattern = FhirPathPattern.parse(config.getString("pattern"));
    List<String> basePaths = config.getStringList("base").stream().distinct().toList();
    List<Registration> pathRegistrations =
        registrations(config, "paths", registry)
            .flatMap(registration -> expandGlob(registration, basePaths))
            .map(Profile::requireFittingPathType)
            .toList();
    Map<String, List<Registration>> pathHandlers =
        basePaths.stream()
            .collect(
                toUnmodifiableMap(
                    Function.identity(), path -> handlersFor(path, pathRegistrations)));
    Map<Class<?>, List<Registration>> typeHandlers =
        registrations(config, "types", registry)
            .collect(
                groupingBy(
                    registration -> requireFittingType(registration, typeFor(registration.key()))));
    return new Module(pattern, pathHandlers, Map.copyOf(typeHandlers));
  }

  private static Stream<Registration> registrations(
      Config config, String section, Registry registry) {
    return Optional.of(section).filter(config::hasPath).stream()
        .flatMap(present -> config.getConfig(present).root().entrySet().stream())
        .map(
            entry -> {
              String name = ((ConfigObject) entry.getValue()).toConfig().getString("handler");
              return new Registration(entry.getKey(), name, registry.resolve(name));
            });
  }

  /** The handler of a {@code types} entry has to accept the type it is registered for. */
  private static Class<?> requireFittingType(Registration registration, Class<?> type) {
    requireAccepts(registration, type, "types section");
    return type;
  }

  /**
   * The handler of a path has to accept the element the path names, and the path has to name one:
   * a handler on a path that names no element can never run.
   */
  private static Registration requireFittingPathType(Registration registration) {
    Class<?> elementType =
        FhirPaths.elementType(registration.key())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Handler '%s' is registered on %s, which names no FHIR element!"
                            .formatted(registration.name(), registration.key())));
    requireAccepts(registration, elementType, registration.key());
    return registration;
  }

  private static void requireAccepts(Registration registration, Class<?> type, String where) {
    Class<?> valueType = registration.registration().valueType();
    if (!valueType.isAssignableFrom(type)) {
      throw new IllegalStateException(
          "Handler '%s' works on %s, not on %s (%s)!"
              .formatted(
                  registration.name(), valueType.getSimpleName(), type.getSimpleName(), where));
    }
  }

  private static List<Registration> handlersFor(String path, List<Registration> registrations) {
    return registrations.stream().filter(registration -> registration.key().equals(path)).toList();
  }

  private static Class<?> typeFor(String typeName) {
    try {
      return Class.forName(MODEL_PACKAGE + typeName);
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(
          "Unknown FHIR type '%s' in types section!".formatted(typeName), e);
    }
  }

  /** A plain path must be in the base list; a {@code *} glob expands against the base list. */
  private static Stream<Registration> expandGlob(
      Registration registration, List<String> basePaths) {
    String path = registration.key();
    if (!path.contains("*")) {
      if (!basePaths.contains(path)) {
        throw new IllegalStateException(
            "trying to register a handler to the unspecified FHIR path %s!".formatted(path));
      }
      return Stream.of(registration);
    }
    Pattern pattern = Pattern.compile(globToRegex(path));
    return basePaths.stream()
        .filter(basePath -> pattern.matcher(basePath).matches())
        .map(registration::withKey);
  }

  private static String globToRegex(String path) {
    String[] elements = path.split("\\.");
    if (elements.length == 1) {
      return elements[0].equals("*") ? ".*" : elementToRegex(elements[0]);
    }
    boolean leadingStar = elements[0].equals("*");
    boolean trailingStar = elements[elements.length - 1].equals("*");
    int from = leadingStar ? 1 : 0;
    int to = trailingStar ? elements.length - 1 : elements.length;
    String inner =
        Arrays.stream(elements, from, to).map(Profile::elementToRegex).collect(joining("\\."));
    String prefix = leadingStar ? "^(|.*\\.)" : "^";
    String suffix = trailingStar ? "(|\\..*)$" : "$";
    return prefix + inner + suffix;
  }

  /**
   * One path element as a regex: everything is literal text except {@code *}, which matches within
   * the element. Path keys carry regex metacharacters, e.g. the brackets of choice elements ({@code
   * deceased[dateTime]}), so the literal parts are quoted.
   */
  private static String elementToRegex(String element) {
    return Arrays.stream(element.split("\\*", -1))
        .map(literal -> literal.isEmpty() ? "" : Pattern.quote(literal))
        .collect(joining("[^\\.]*"));
  }
}
