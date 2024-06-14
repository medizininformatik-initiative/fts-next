package care.smith.fts.util.deidentifhir;

import care.smith.fts.util.tca.IDMap;
import de.ume.deidentifhir.util.IDReplacementProvider;
import de.ume.deidentifhir.util.IdentifierValueReplacementProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class NamespacingService
    implements IDReplacementProvider, IdentifierValueReplacementProvider {
  private final Map<String, String> ids;
  private final KeyCreator keyCreator;

  public static NamespacingService withNamespacing(String prefix, IDMap ids) {
    return new NamespacingService(new NamespacingEnabled(prefix), ids);
  }

  public static NamespacingService withNamespacing(String prefix) {
    return new NamespacingService(new NamespacingEnabled(prefix), Map.of());
  }

  public static NamespacingService withoutNamespacing(Set<String> ids) {

    return new NamespacingService(new NamespacingDisabled(), toMap(ids));
  }

  public static NamespacingService withoutNamespacing(IDMap idMap) {

    return new NamespacingService(new NamespacingDisabled(), idMap);
  }

  private static Map<String, String> toMap(Set<String> set) {
    return set.stream().collect(Collectors.toMap(Function.identity(), Function.identity()));
  }

  private NamespacingService(KeyCreator keyCreator, Map<String, String> ids) {
    this.ids = ids;
    this.keyCreator = keyCreator;
  }

  @Override
  public String getIDReplacement(@NotNull String resourceType, @NotBlank String id) {
    String key = keyCreator.getKeyForResourceTypeAndID(resourceType, id);

    if (ids.containsKey(key)) {
      return ids.get(key);
    } else {
      throw new RuntimeException("no valid mapping found for id: " + key);
    }
  }

  @Override
  public String getValueReplacement(@NotBlank String system, @NotBlank String value) {
    String key = keyCreator.getKeyForSystemAndValue(system, value);

    if (ids.containsKey(key)) {
      return ids.get(key);
    } else {
      throw new RuntimeException("no valid mapping found for value: " + key);
    }
  }

  public String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotBlank String id) {
    return keyCreator.getKeyForResourceTypeAndID(resourceType, id);
  }

  public String getKeyForSystemAndValue(@NotNull String system, @NotNull String value) {
    return keyCreator.getKeyForSystemAndValue(system, value);
  }

  interface KeyCreator {
    String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotBlank String id);

    String getKeyForSystemAndValue(@NotBlank String system, @NotBlank String value);
  }

  static class NamespacingEnabled implements KeyCreator {
    private final String prefix;

    public NamespacingEnabled(String prefix) {
      this.prefix = prefix;
    }

    public String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotBlank String id) {
      return prefix + ".id." + resourceType + ":" + id;
    }

    public String getKeyForSystemAndValue(@NotBlank String system, @NotBlank String value) {
      return prefix + ".identifier." + system + ":" + value;
    }
  }

  static class NamespacingDisabled implements KeyCreator {
    public String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotBlank String id) {
      return id;
    }

    public String getKeyForSystemAndValue(@NotBlank String system, @NotBlank String value) {
      return value;
    }
  }
}
