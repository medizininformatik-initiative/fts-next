package care.smith.fts.rda.services.deidentifhir;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.Objects;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;

/** TransportID to Pseudonym */
@Deprecated(forRemoval = true)
public interface DeidentifhirUtil {

  static Registry generateRegistry(Map<String, String> secureMapping) {
    var keyCreator = NamespacingReplacementProvider.withoutNamespacing();
    var replacementProvider = NamespacingReplacementProvider.of(keyCreator, secureMapping);
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
  static Bundle deidentify(
      Config config, Registry registry, Bundle bundle, MeterRegistry meterRegistry) {
    var sample = Timer.start(meterRegistry);
    var deidentifhir = Deidentifhir.apply(config, registry);
    var deidentified = (Bundle) deidentifhir.deidentify(bundle);
    sample.stop(meterRegistry.timer("deidentify"));
    return deidentified;
  }

  /**
   * Restores shifted dates from TCA. Finds date elements with tID extensions, looks up the shifted
   * date using the tID, sets the date value, and removes the extension.
   *
   * @param bundle the bundle containing date elements with tID extensions
   * @param dateShiftMap mapping from tID to shifted date (ISO-8601 string)
   */
  static void restoreShiftedDates(Bundle bundle, Map<String, String> dateShiftMap) {
    bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Objects::nonNull)
        .forEach(resource -> restoreShiftedDatesInBase(resource, dateShiftMap));
  }

  private static void restoreShiftedDatesInBase(Base base, Map<String, String> dateShiftMap) {
    base.children()
        .forEach(
            property ->
                property
                    .getValues()
                    .forEach(
                        value -> {
                          if (value instanceof BaseDateTimeType dateTimeType) {
                            restoreDateIfNeeded(dateTimeType, dateShiftMap);
                          } else {
                            restoreShiftedDatesInBase((Base) value, dateShiftMap);
                          }
                        }));
  }

  private static void restoreDateIfNeeded(
      BaseDateTimeType dateTimeType, Map<String, String> dateShiftMap) {
    Extension extension = dateTimeType.getExtensionByUrl(DATE_SHIFT_EXTENSION_URL);
    if (extension != null) {
      String tId = ((StringType) extension.getValue()).getValue();
      String shiftedDate = dateShiftMap.get(tId);
      if (shiftedDate != null) {
        dateTimeType.setValueAsString(shiftedDate);
      }
      dateTimeType.removeExtension(DATE_SHIFT_EXTENSION_URL);
    }
  }
}
