package care.smith.fts.deidentifier.allowlist;

import static com.typesafe.config.ConfigUtil.joinPath;
import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toUnmodifiableMap;

import care.smith.fts.deidentifier.Registry;
import care.smith.fts.deidentifier.Registry.Registration;
import com.typesafe.config.Config;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
    return new Module(
        FhirPathPattern.parse(config.getString("pattern")),
        Set.copyOf(config.getStringList("base")),
        pathHandlers(config, registry));
  }

  private static Map<String, List<Registration>> pathHandlers(Config config, Registry registry) {
    return Optional.of("paths")
        .filter(config::hasPath)
        .map(config::getConfig)
        .map(paths -> handlersByPath(paths, registry))
        .orElse(Map.of());
  }

  private static Map<String, List<Registration>> handlersByPath(Config paths, Registry registry) {
    return paths.root().keySet().stream()
        .collect(
            toUnmodifiableMap(
                identity(),
                path -> List.of(registry.resolve(child(paths, path).getString("handler")))));
  }
}
