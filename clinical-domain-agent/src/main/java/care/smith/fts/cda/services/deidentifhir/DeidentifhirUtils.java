package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.StringType;
import scala.Function4;
import scala.collection.immutable.Map;
import scala.collection.immutable.Seq;

public interface DeidentifhirUtils {

  static BaseDateTimeType shiftDate(
      BaseDateTimeType date, java.util.Map<String, String> dateValueToTid) {
    if (date != null && date.getValue() != null) {
      var dateValue = date.getValueAsString();
      var tId = dateValueToTid.get(dateValue);
      if (tId != null) {
        date.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType(tId));
        date.setValue(null);
      }
    }
    return date;
  }

  static Registry generateRegistry(
      String patientId,
      java.util.Map<String, String> transportIds,
      java.util.Map<String, String> dateTransportMappings) {
    var keyCreator = NamespacingReplacementProvider.withNamespacing(patientId);
    var replacementProvider = NamespacingReplacementProvider.of(keyCreator, transportIds);

    // Invert tID→dateValue to dateValue→tID for lookup during date processing.
    // DataScraper guarantees unique date values in dateTransportMappings, so inversion is safe.
    var dateValueToTid =
        dateTransportMappings.entrySet().stream()
            .collect(toMap(java.util.Map.Entry::getValue, java.util.Map.Entry::getKey));

    Registry registry = new Registry();
    registry.addHander("postalCodeHandler", Handlers.generalizePostalCodeHandler().get());
    registry.addHander(
        "generalizeDateHandler",
        (Function4<Seq<String>, DateType, Seq<Base>, Map<String, String>, DateType>)
            Handlers::generalizeDateHandler);
    registry.addHander(
        "PSEUDONYMISIERTstringReplacementHandler",
        JavaCompat.partiallyApply("PSEUDONYMISIERT", Handlers::stringReplacementHandler));
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

    // Handler that adds tID extension to dates and nulls the value
    // RDA will resolve tIDs to shifted dates from TCA
    registry.addHander(
        "shiftDateHandler",
        (Function4<Seq<String>, BaseDateTimeType, Seq<Base>, Map<String, String>, BaseDateTimeType>)
            (path, date, parents, context) -> shiftDate(date, dateValueToTid));

    return registry;
  }

  static Bundle deidentify(
      Config config,
      Registry registry,
      Bundle bundle,
      String patientId,
      MeterRegistry meterRegistry) {
    var sample = Timer.start(meterRegistry);

    Map<String, String> staticContext = new Map.Map1<>(Handlers.patientIdentifierKey(), patientId);
    Deidentifhir deidentifhir = Deidentifhir.apply(config, registry);
    var deidentified = (Bundle) deidentifhir.deidentify(bundle, staticContext);
    sample.stop(meterRegistry.timer("deidentify"));
    return deidentified;
  }
}
