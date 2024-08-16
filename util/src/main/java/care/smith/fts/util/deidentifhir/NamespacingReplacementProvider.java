package care.smith.fts.util.deidentifhir;

import de.ume.deidentifhir.util.IDReplacementProvider;
import de.ume.deidentifhir.util.IdentifierValueReplacementProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public class NamespacingReplacementProvider
    implements IDReplacementProvider, IdentifierValueReplacementProvider {
  private final Map<String, String> ids;
  private final KeyCreator keyCreator;

  public static KeyCreator withNamespacing(String prefix) {
    return new NamespacingEnabled(prefix);
  }

  public static KeyCreator withoutNamespacing() {
    return new NamespacingDisabled();
  }

  public static NamespacingReplacementProvider of(KeyCreator keyCreator) {
    return new NamespacingReplacementProvider(keyCreator, Map.of());
  }

  public static NamespacingReplacementProvider of(KeyCreator keyCreator, Map<String, String> ids) {
    return new NamespacingReplacementProvider(keyCreator, ids);
  }

  private NamespacingReplacementProvider(KeyCreator keyCreator, Map<String, String> ids) {
    this.ids = ids;
    this.keyCreator = keyCreator;
  }

  @Override
  public String getIDReplacement(@NotNull String resourceType, @NotBlank String id) {
    String key = keyCreator.getKeyForResourceTypeAndID(resourceType, id);

    if (ids.containsKey(key)) {
      return ids.get(key);
    } else {
      throw new IllegalArgumentException("no valid mapping found for id: " + key);
    }
  }

  @Override
  public String getValueReplacement(@NotBlank String system, @NotBlank String value) {
    String key = keyCreator.getKeyForSystemAndValue(system, value);

    if (ids.containsKey(key)) {
      return ids.get(key);
    } else {
      throw new IllegalArgumentException("no valid mapping found for value: " + key);
    }
  }

  public interface KeyCreator {
    String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotBlank String id);

    String getKeyForSystemAndValue(@NotBlank String system, @NotBlank String value);
  }

  static class NamespacingEnabled implements KeyCreator {
    private final String prefix;

    public NamespacingEnabled(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotBlank String id) {
      return prefix + "." + resourceType + ":" + id;
    }

    @Override
    public String getKeyForSystemAndValue(@NotBlank String system, @NotBlank String value) {
      return prefix + ".identifier." + system + ":" + value;
    }
  }

  static class NamespacingDisabled implements KeyCreator {
    @Override
    public String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotBlank String id) {
      return id;
    }

    @Override
    public String getKeyForSystemAndValue(@NotBlank String system, @NotBlank String value) {
      return value;
    }
  }
}
