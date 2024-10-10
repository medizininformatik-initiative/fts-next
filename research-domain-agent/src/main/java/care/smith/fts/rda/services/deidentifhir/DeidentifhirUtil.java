package care.smith.fts.rda.services.deidentifhir;

import care.smith.fts.util.deidentifhir.DateShiftingProvider;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Map;
import org.hl7.fhir.r4.model.Bundle;

/** TransportID to Pseudonym */
public interface DeidentifhirUtil {

  static Registry generateRegistry(
      Map<String, String> transportIdToPseudonym, Duration dateShiftValue) {
    var keyCreator = NamespacingReplacementProvider.withoutNamespacing();
    var replacementProvider = NamespacingReplacementProvider.of(keyCreator, transportIdToPseudonym);
    DateShiftingProvider dsp = new DateShiftingProvider(dateShiftValue);
    Registry registry = new Registry();
    registry.addHander(
        "idReplacementHandler",
        JavaCompat.partiallyApply(replacementProvider, Handlers::idReplacementHandler));
    registry.addHander(
        "shiftDateHandler", JavaCompat.partiallyApply(dsp, Handlers::shiftDateHandler));
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
  static Bundle deidentify(
      Config config, Registry registry, Bundle bundle, MeterRegistry meterRegistry) {
    var sample = Timer.start(meterRegistry);
    var deidentifhir = Deidentifhir.apply(config, registry);
    var deidentified = (Bundle) deidentifhir.deidentify(bundle);
    sample.stop(meterRegistry.timer("replaceIDs"));
    return deidentified;
  }
}
