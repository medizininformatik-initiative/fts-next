package care.smith.fts.rda.services.deidentifhir;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;

/** TransportID to Pseudonym */
public interface DeidentifhirUtil {

  static Registry generateRegistry(Map<String, String> transportIdToPseudonym) {
    var keyCreator = NamespacingReplacementProvider.withoutNamespacing();
    var replacementProvider = NamespacingReplacementProvider.of(keyCreator, transportIdToPseudonym);
    Registry registry = new Registry();
    registry.addHander(
        "idReplacementHandler",
        JavaCompat.partiallyApply(replacementProvider, Handlers::idReplacementHandler));
    registry.addHander(
        "referenceReplacementHandler",
        JavaCompat.partiallyApply(replacementProvider, Handlers::referenceReplacementHandler));
    registry.addHander(
        "identifierValueReplacementHandler",
        JavaCompat.partiallyApply2(
            replacementProvider, true, Handlers::identifierValueReplacementHandler));
    registry.addHander(
        "conditionalReferencesReplacementHandler",
        JavaCompat.partiallyApply2(
            replacementProvider,
            replacementProvider,
            Handlers::conditionalReferencesReplacementHandler));
    return registry;
  }

  /**
   * Replace all IDs contained in the provided bundle with the replacement stored in the provided
   * pseudonymMap.
   */
  static Bundle replaceIDs(Config config, Registry registry, Bundle bundle) {
    var deidentifhir = Deidentifhir.apply(config, registry);
    return (Bundle) deidentifhir.deidentify(bundle);
  }
}
