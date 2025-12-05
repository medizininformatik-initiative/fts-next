package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider.KeyCreator;
import de.ume.deidentifhir.util.IDReplacementProvider;
import de.ume.deidentifhir.util.IdentifierValueReplacementProvider;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public class ScrapingStorage implements IDReplacementProvider, IdentifierValueReplacementProvider {
  @Getter Set<String> compartmentIds = new HashSet<>();
  @Getter Set<String> nonCompartmentIds = new HashSet<>();
  private final KeyCreator namespacingService;

  /**
   * Map of "ResourceType:id" -> isInCompartment. When set, determines whether to apply patient ID
   * prefix to resource IDs.
   */
  @Setter private Map<String, Boolean> compartmentMembership = Map.of();

  public ScrapingStorage(KeyCreator namespacingService) {
    this.namespacingService = namespacingService;
  }

  @Override
  public String getIDReplacement(String resourceType, String id) {
    String lookupKey = resourceType + ":" + id;
    boolean inCompartment = compartmentMembership.getOrDefault(lookupKey, true);

    String key;
    if (inCompartment) {
      key = namespacingService.getKeyForResourceTypeAndID(resourceType, id);
      compartmentIds.add(key);
    } else {
      key = resourceType + ":" + id;
      nonCompartmentIds.add(key);
    }
    return id;
  }

  @Override
  public String getValueReplacement(String system, String value) {
    compartmentIds.add(namespacingService.getKeyForSystemAndValue(system, value));
    return value;
  }
}
