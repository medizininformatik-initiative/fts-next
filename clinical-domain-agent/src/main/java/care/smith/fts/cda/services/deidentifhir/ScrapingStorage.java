package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider.KeyCreator;
import de.ume.deidentifhir.util.IDReplacementProvider;
import de.ume.deidentifhir.util.IdentifierValueReplacementProvider;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;

public class ScrapingStorage implements IDReplacementProvider, IdentifierValueReplacementProvider {
  @Getter Set<String> gatheredIDATs = new HashSet<>();
  private final KeyCreator namespacingService;

  public ScrapingStorage(KeyCreator namespacingService) {
    this.namespacingService = namespacingService;
  }

  @Override
  public String getIDReplacement(String resourceType, String id) {
    gatheredIDATs.add(namespacingService.getKeyForResourceTypeAndID(resourceType, id));
    return id;
  }

  @Override
  public String getValueReplacement(String system, String value) {
    gatheredIDATs.add(namespacingService.getKeyForSystemAndValue(system, value));
    return value;
  }
}
