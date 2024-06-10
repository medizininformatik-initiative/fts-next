package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.util.tca.TransportIDs;
import de.ume.deidentifhir.util.IDReplacementProvider;
import de.ume.deidentifhir.util.IdentifierValueReplacementProvider;
import jakarta.validation.constraints.NotNull;

public class PseudonymProvider
    implements IDReplacementProvider, IdentifierValueReplacementProvider {

  private final NamespacingService namespacingService;
  private final TransportIDs transportIds;

  public PseudonymProvider(NamespacingService namespacingService, TransportIDs transportIds) {
    this.namespacingService = namespacingService;
    this.transportIds = transportIds;
  }

  @Override
  public String getIDReplacement(@NotNull String resourceType, @NotNull String id) {
    String key = namespacingService.getKeyForResourceTypeAndID(resourceType, id);

    if (transportIds.containsKey(key)) {
      return transportIds.get(key);
    } else {
      throw new RuntimeException("no valid mapping found for id: " + key);
    }
  }

  @Override
  public String getValueReplacement(@NotNull String system, @NotNull String value) {
    String key = namespacingService.getKeyForSystemAndValue(system, value);

    if (transportIds.containsKey(key)) {
      return transportIds.get(key);
    } else {
      throw new RuntimeException("no valid mapping found for value: " + key);
    }
  }
}
