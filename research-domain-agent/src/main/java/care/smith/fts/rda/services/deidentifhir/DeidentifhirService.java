package care.smith.fts.rda.services.deidentifhir;

import care.smith.fts.util.deidentifhir.NamespacingService;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;

/** TransportID to Pseudonym */
public class DeidentifhirService {
  private final Deidentifhir deidentifhir;

  public DeidentifhirService(Config config, Map<String, String> transportIdToPseudonym) {
    NamespacingService namespacingService =
        NamespacingService.withoutNamespacing(transportIdToPseudonym);
    Registry registry = new Registry();
    registry.addHander(
        "idReplacementHandler",
        JavaCompat.partiallyApply(namespacingService, Handlers::idReplacementHandler));
    registry.addHander(
        "referenceReplacementHandler",
        JavaCompat.partiallyApply(namespacingService, Handlers::referenceReplacementHandler));
    registry.addHander(
        "identifierValueReplacementHandler",
        JavaCompat.partiallyApply2(
            namespacingService, true, Handlers::identifierValueReplacementHandler));
    registry.addHander(
        "conditionalReferencesReplacementHandler",
        JavaCompat.partiallyApply2(
            namespacingService,
            namespacingService,
            Handlers::conditionalReferencesReplacementHandler));

    deidentifhir = Deidentifhir.apply(config, registry);
  }

  /**
   * Replace all IDs contained in the provided bundle with the replacement stored in the provided
   * pseudonymMap.
   */
  public Bundle replaceIDs(Bundle bundle) {
    return (Bundle) deidentifhir.deidentify(bundle);
  }
}
