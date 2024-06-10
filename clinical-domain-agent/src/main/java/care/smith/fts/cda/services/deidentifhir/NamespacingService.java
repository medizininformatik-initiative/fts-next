package care.smith.fts.cda.services.deidentifhir;

import jakarta.validation.constraints.NotNull;

public class NamespacingService {
  private final String prefix;

  public NamespacingService(String prefix) {
    this.prefix = prefix;
  }

  public String getKeyForResourceTypeAndID(@NotNull String resourceType, @NotNull String id) {
    return prefix + ".id." + resourceType + ":" + id;
  }

  public String getKeyForSystemAndValue(@NotNull String system, @NotNull String value) {
    return prefix + ".identifier." + system + ":" + value;
  }
}
