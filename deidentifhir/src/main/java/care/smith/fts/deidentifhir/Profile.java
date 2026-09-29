package care.smith.fts.deidentifhir;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableMap;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Resource;

/**
 * A parsed de-identification profile: the whole HOCON configuration, resolved against a {@link
 * Registry} once. The engine asks it one question: which rule applies to an element.
 */
public final class Profile {

  /** The package the {@code types} sections name their classes in, without their package. */
  private static final String MODEL_PACKAGE = "org.hl7.fhir.r4.model.";

  /**
   * The decision for one element. {@link #REMOVE} means no module keeps it. {@link Apply} carries
   * the merged handler chain of every applicable module, type handlers before path handlers; an
   * empty chain means keep unchanged.
   */
  public sealed interface Rule {

    Rule REMOVE = new Remove();

    record Remove() implements Rule {}

    record Apply(List<DeidentifhirHandler<Object>> handlers) implements Rule {}
  }

  /** One module of the profile: a pattern that selects resources, plus its handlers. */
  private record Module(
      FhirPathPattern pattern,
      Map<String, List<DeidentifhirHandler<Object>>> pathHandlers,
      Map<Class<?>, List<DeidentifhirHandler<Object>>> typeHandlers) {}

  /** One {@code key { handler = name }} entry of a {@code paths} or {@code types} section. */
  private record Registration(String key, DeidentifhirHandler<Object> handler) {}

  private final List<Module> modules;

  private Profile(List<Module> modules) {
    this.modules = List.copyOf(modules);
  }

  public static Profile parse(Config config, Registry registry) {
    requireNonNull(config);
    requireNonNull(registry);
    ConfigObject modules = config.getObject("modules");
    return new Profile(
        modules.keySet().stream()
            .map(key -> parseModule(modules.toConfig().getConfig(key), registry))
            .toList());
  }

  /**
   * The rules that apply to one resource: the modules whose patterns match it, resolved once. The
   * patterns are constant per resource, so the engine asks once and queries the result for every
   * element instead of re-matching per primitive.
   */
  public static final class Rules {

    private final List<Module> matched;

    private Rules(List<Module> matched) {
      this.matched = matched;
    }

    /**
     * The merged decision for one element: the type handlers of all matched modules run before the
     * path handlers of all matched modules. An element that no matched module lists in its base is
     * removed, whatever type handlers exist for it; one that a module lists without a handler is
     * kept unchanged.
     */
    public Rule ruleFor(List<String> path, Class<?> valueType) {
      String pathKey = String.join(".", path);
      List<List<DeidentifhirHandler<Object>>> typeChains =
          chains(module -> module.typeHandlers().get(valueType));
      List<List<DeidentifhirHandler<Object>>> pathChains =
          chains(module -> module.pathHandlers().get(pathKey));
      // the base list decides what is kept; a type handler only transforms a kept element
      if (pathChains.isEmpty()) {
        return Rule.REMOVE;
      }
      return new Rule.Apply(
          Stream.concat(typeChains.stream(), pathChains.stream()).flatMap(List::stream).toList());
    }

    private List<List<DeidentifhirHandler<Object>>> chains(
        Function<Module, List<DeidentifhirHandler<Object>>> lookup) {
      return matched.stream()
          .flatMap(module -> Optional.ofNullable(lookup.apply(module)).stream())
          .toList();
    }
  }

  public Rules rulesFor(Resource resource) {
    return new Rules(modules.stream().filter(m -> m.pattern().matches(resource)).toList());
  }

  /** The merged decision of every module whose pattern matches {@code resource}. */
  public Rule ruleFor(Resource resource, List<String> path, Class<?> valueType) {
    return rulesFor(resource).ruleFor(path, valueType);
  }

  private static Module parseModule(Config config, Registry registry) {
    FhirPathPattern pattern = FhirPathPattern.parse(config.getString("pattern"));
    List<String> basePaths = config.getStringList("base").stream().distinct().toList();
    List<Registration> pathRegistrations =
        registrations(config, "paths", registry)
            .flatMap(registration -> expandGlob(registration, basePaths))
            .toList();
    Map<String, List<DeidentifhirHandler<Object>>> pathHandlers =
        basePaths.stream()
            .collect(
                toUnmodifiableMap(
                    Function.identity(), path -> handlersFor(path, pathRegistrations)));
    Map<Class<?>, List<DeidentifhirHandler<Object>>> typeHandlers =
        registrations(config, "types", registry)
            .collect(
                groupingBy(
                    registration -> typeFor(registration.key()),
                    mapping(Registration::handler, toList())));
    return new Module(pattern, pathHandlers, Map.copyOf(typeHandlers));
  }

  private static Stream<Registration> registrations(
      Config config, String section, Registry registry) {
    return Optional.of(section).filter(config::hasPath).stream()
        .flatMap(present -> config.getConfig(present).root().entrySet().stream())
        .map(
            entry ->
                new Registration(
                    entry.getKey(),
                    registry
                        .resolve(((ConfigObject) entry.getValue()).toConfig().getString("handler"))
                        .handler()));
  }

  private static List<DeidentifhirHandler<Object>> handlersFor(
      String path, List<Registration> registrations) {
    return registrations.stream()
        .filter(registration -> registration.key().equals(path))
        .map(Registration::handler)
        .toList();
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
        .map(basePath -> new Registration(basePath, registration.handler()));
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
