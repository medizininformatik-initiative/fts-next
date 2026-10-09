package care.smith.fts.deidentifier.allowlist;

import static com.typesafe.config.ConfigUtil.joinPath;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toUnmodifiableMap;

import care.smith.fts.deidentifier.Registry;
import care.smith.fts.deidentifier.Registry.Registration;
import care.smith.fts.deidentifier.allowlist.NamedModule.Named;
import care.smith.fts.deidentifier.internal.FhirPaths;
import com.typesafe.config.Config;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.PrimitiveType;

/**
 * Reads the HOCON profile format of deidentifhir into a {@link Profile}. Handler names are resolved
 * against a {@link Registry} once, at parse time.
 */
public interface HoconProfile {

  static Profile parse(Config config, Registry registry) {
    requireNonNull(config);
    requireNonNull(registry);
    Config modules = config.getConfig("modules");
    List<NamedModule> parsed =
        modules.root().keySet().stream()
            .map(name -> parseModule(child(modules, name), registry))
            .toList();
    NamedModule.requireTerminalsLast(parsed);
    return new Profile(parsed.stream().map(NamedModule::toModule).toList());
  }

  /** Reads a child by its key, which may contain dots, instead of by a HOCON path. */
  private static Config child(Config parent, String key) {
    return parent.getConfig(joinPath(key));
  }

  private static NamedModule parseModule(Config config, Registry registry) {
    Map<String, Class<?>> base = baseTypes(config.getStringList("base"));
    return new NamedModule(
        FhirPathPattern.parse(config.getString("pattern")),
        base,
        pathHandlers(config, base, registry),
        typeHandlers(config, registry));
  }

  private static Map<Class<?>, List<Named>> typeHandlers(Config config, Registry registry) {
    return Optional.of("types")
        .filter(config::hasPath)
        .map(config::getConfig)
        .map(types -> handlersByType(types, registry))
        .orElse(Map.of());
  }

  private static Map<Class<?>, List<Named>> handlersByType(Config types, Registry registry) {
    return types.root().keySet().stream()
        .map(key -> Map.entry(primitiveType(key), child(types, key)))
        .collect(
            toUnmodifiableMap(
                Entry::getKey,
                e -> List.of(handler(e.getValue(), e.getKey(), "types section", registry))));
  }

  /**
   * The primitive type a {@code types} key names. The engine only asks for rules of primitive
   * elements, and a handler runs on the exact class of an element, so only a concrete primitive
   * type can ever match.
   */
  private static Class<?> primitiveType(String key) {
    Class<?> type = loadHapiClass(key);
    if (!PrimitiveType.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
      throw new IllegalStateException(
          "The types entry %s is not a concrete FHIR primitive type, so it never matches an element!"
              .formatted(key));
    }
    return type;
  }

  private static Class<?> loadHapiClass(String key) {
    try {
      return Class.forName(
          "org.hl7.fhir.r4.model." + key, false, PrimitiveType.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("The types entry %s names no HAPI class!".formatted(key), e);
    }
  }

  /** The HAPI class of the element that each base path names. */
  private static Map<String, Class<?>> baseTypes(List<String> base) {
    return base.stream()
        .distinct()
        .collect(toUnmodifiableMap(path -> path, HoconProfile::elementType));
  }

  /**
   * The class of the element at {@code path}, which has to be a leaf: the engine asks the rule set
   * only at primitives.
   */
  private static Class<?> elementType(String path) {
    var type =
        FhirPaths.elementType(path)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "The base path %s names no FHIR element!".formatted(path)));
    if (inNarrative(path)) {
      throw new IllegalStateException(
          "The base path %s is in the narrative, which de-identification always drops!"
              .formatted(path));
    }
    if (!PrimitiveType.class.isAssignableFrom(type)) {
      throw new IllegalStateException(
          "The base path %s names a composite element, which the engine never visits; list its leaf paths!"
              .formatted(path));
    }
    return type;
  }

  /** Whether {@code path} or any prefix of it is a narrative, which the engine drops unvisited. */
  private static boolean inNarrative(String path) {
    var elements = List.of(path.split("\\."));
    return IntStream.rangeClosed(1, elements.size())
        .mapToObj(n -> String.join(".", elements.subList(0, n)))
        .map(FhirPaths::elementType)
        .flatMap(Optional::stream)
        .anyMatch(Narrative.class::equals);
  }

  private static Map<String, List<Named>> pathHandlers(
      Config config, Map<String, Class<?>> base, Registry registry) {
    return Optional.of("paths")
        .filter(config::hasPath)
        .map(config::getConfig)
        .map(paths -> handlersByPath(paths, base, registry))
        .orElse(Map.of());
  }

  private static Map<String, List<Named>> handlersByPath(
      Config paths, Map<String, Class<?>> base, Registry registry) {
    Map<String, List<String>> keysByPath = keysByPath(paths.root().keySet(), base.keySet());
    requireOneKeyPerPath(keysByPath);
    return keysByPath.entrySet().stream()
        .collect(
            toUnmodifiableMap(
                Entry::getKey,
                e -> {
                  String path = e.getKey();
                  return List.of(
                      handler(paths, e.getValue().getFirst(), path, base.get(path), registry));
                }));
  }

  /** The {@code paths} keys that reach each base path. */
  private static Map<String, List<String>> keysByPath(Set<String> keys, Set<String> base) {
    return keys.stream()
        .flatMap(key -> expand(key, base).stream().map(path -> Map.entry(path, key)))
        .collect(groupingBy(Entry::getKey, mapping(Entry::getValue, toList())));
  }

  /** The handler of a {@code paths} key, which has to work on the element type of {@code path}. */
  private static Named handler(
      Config paths, String key, String path, Class<?> elementType, Registry registry) {
    return handler(child(paths, key), elementType, path, registry);
  }

  /**
   * Resolves the handler that {@code entry} names and checks that it works on {@code elementType};
   * {@code context} says where the entry is, for the error message.
   */
  private static Named handler(
      Config entry, Class<?> elementType, String context, Registry registry) {
    String name = entry.getString("handler");
    Registration registration = registry.resolve(name);
    if (!registration.valueType().isAssignableFrom(elementType)) {
      throw new IllegalStateException(
          "Handler '%s' works on %s, not on %s (%s)!"
              .formatted(
                  name,
                  registration.valueType().getSimpleName(),
                  elementType.getSimpleName(),
                  context));
    }
    return new Named(name, registration);
  }

  /** A path has one handler per module, so at most one {@code paths} key may reach it. */
  private static void requireOneKeyPerPath(Map<String, List<String>> keysByPath) {
    keysByPath.entrySet().stream()
        .filter(e -> e.getValue().size() > 1)
        .findFirst()
        .ifPresent(
            e -> {
              throw new IllegalStateException(
                  "The paths entries %s both reach the path %s, but a path has one handler!"
                      .formatted(String.join(" and ", e.getValue()), e.getKey()));
            });
  }

  /** The base paths a {@code paths} key stands for; a key without {@code *} stands for itself. */
  private static List<String> expand(String key, Set<String> base) {
    Pattern pattern = Pattern.compile(globToRegex(key));
    List<String> matches = base.stream().filter(path -> pattern.matcher(path).matches()).toList();
    if (matches.isEmpty()) {
      throw new IllegalStateException(
          "The paths entry %s matches no path of the base list!".formatted(key));
    }
    return matches;
  }

  /**
   * A {@code *} inside a path element matches within that element. A leading {@code *.} also
   * matches any number of whole leading elements, a trailing {@code .*} any number of whole
   * trailing elements, and a lone {@code *} every path. Everything else is literal: path keys carry
   * regex metacharacters, e.g. the brackets of {@code deceased[dateTime]}.
   */
  private static String globToRegex(String glob) {
    if (glob.equals("*")) {
      return ".*";
    }
    String[] elements = glob.split("\\.", -1);
    boolean leading = elements[0].equals("*");
    boolean trailing = elements[elements.length - 1].equals("*");
    String inner =
        Arrays.stream(elements, leading ? 1 : 0, trailing ? elements.length - 1 : elements.length)
            .map(HoconProfile::elementToRegex)
            .collect(joining("\\."));
    return (leading ? "(|.*\\.)" : "") + inner + (trailing ? "(|\\..*)" : "");
  }

  private static String elementToRegex(String element) {
    return Arrays.stream(element.split("\\*", -1)).map(Pattern::quote).collect(joining("[^.]*"));
  }
}
