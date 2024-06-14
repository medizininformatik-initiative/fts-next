package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.util.deidentifhir.NamespacingService;
import de.ume.deidentifhir.util.IDReplacementProvider;
import de.ume.deidentifhir.util.IdentifierValueReplacementProvider;
import java.util.HashSet;
import lombok.Getter;

public class ScrapingStorage implements IDReplacementProvider, IdentifierValueReplacementProvider {
  @Getter HashSet<String> gatheredIDATs = new HashSet<>();
  private final NamespacingService namespacingService;

  public ScrapingStorage(NamespacingService namespacingService) {
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
