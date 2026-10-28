package care.smith.fts.deidentifier.allowlist;

import static com.typesafe.config.ConfigUtil.joinPath;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableMap;

import care.smith.fts.deidentifier.Registry;
import care.smith.fts.deidentifier.Registry.Registration;
import com.typesafe.config.Config;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
    return new Profile(
        modules.root().keySet().stream()
            .map(name -> parseModule(child(modules, name), registry))
            .toList());
  }

  /** Reads a child by its key, which may contain dots, instead of by a HOCON path. */
  private static Config child(Config parent, String key) {
    return parent.getConfig(joinPath(key));
  }

  private static Module parseModule(Config config, Registry registry) {
    Map<String, Class<?>> base = baseTypes(config.getStringList("base"));
    return new Module(
        FhirPathPattern.parse(config.getString("pattern")),
        base.keySet(),
        pathHandlers(config, base, registry),
        typeHandlers(config, registry));
  }

  private static Map<Class<?>, List<Registration>> typeHandlers(Config config, Registry registry) {
    return Optional.of("types")
        .filter(config::hasPath)
        .map(config::getConfig)
        .map(types -> handlersByType(types, registry))
        .orElse(Map.of());
  }

  private static Map<Class<?>, List<Registration>> handlersByType(Config types, Registry registry) {
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
    requireConcretePrimitive(key, type);
    return type;
  }

  private static void requireConcretePrimitive(String key, Class<?> type) {
    if (!PrimitiveType.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
      throw new IllegalStateException(
          "The types entry %s is not a concrete FHIR primitive type, so it never matches an element!"
              .formatted(key));
    }
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
    return base.stream().distinct().collect(toUnmodifiableMap(path -> path, BasePaths::leafType));
  }

  private static Map<String, List<Registration>> pathHandlers(
      Config config, Map<String, Class<?>> base, Registry registry) {
    return Optional.of("paths")
        .filter(config::hasPath)
        .map(config::getConfig)
        .map(paths -> handlersByPath(paths, base, registry))
        .orElse(Map.of());
  }

  private static Map<String, List<Registration>> handlersByPath(
      Config paths, Map<String, Class<?>> base, Registry registry) {
    return PathGlobs.keyPerPath(paths.root().keySet(), base.keySet()).entrySet().stream()
        .collect(toUnmodifiableMap(Entry::getKey, e -> pathHandler(paths, e, base, registry)));
  }

  /**
   * The handler of the {@code paths} key that reaches a base path, which has to work on the element
   * type of that path.
   */
  private static List<Registration> pathHandler(
      Config paths,
      Entry<String, String> keyOfPath,
      Map<String, Class<?>> base,
      Registry registry) {
    var path = keyOfPath.getKey();
    return List.of(handler(child(paths, keyOfPath.getValue()), base.get(path), path, registry));
  }

  /**
   * Resolves the handler that {@code entry} names and checks that it works on {@code elementType};
   * {@code context} says where the entry is, for the error message.
   */
  private static Registration handler(
      Config entry, Class<?> elementType, String context, Registry registry) {
    String name = entry.getString("handler");
    Registration registration = registry.resolve(name);
    registration.requireAccepts(elementType, context);
    return registration;
  }
}
