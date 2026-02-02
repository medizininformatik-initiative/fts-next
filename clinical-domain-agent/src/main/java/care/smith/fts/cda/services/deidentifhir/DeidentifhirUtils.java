package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;

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

  /**
   * Builds a registry with handlers that use the provided GeneratingReplacementProvider. During
   * deidentification, the provider generates tIDs on-the-fly for IDs and dates.
   *
   * @param provider the replacement provider that generates and caches tIDs
   * @return configured registry for deidentification
   */
  static Registry buildRegistry(GeneratingReplacementProvider provider) {
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
        JavaCompat.partiallyApply(provider, Handlers::idReplacementHandler));
    registry.addHander(
        "referenceReplacementHandler",
        JavaCompat.partiallyApply(provider, Handlers::referenceReplacementHandler));
    registry.addHander(
        "identifierValueReplacementHandler",
        JavaCompat.partiallyApply2(provider, true, Handlers::identifierValueReplacementHandler));
    registry.addHander(
        "conditionalReferencesReplacementHandler",
        JavaCompat.partiallyApply2(
            provider, provider, Handlers::conditionalReferencesReplacementHandler));

    // Handler that generates tID for date, adds extension, and nulls the value
    registry.addHander(
        "shiftDateHandler",
        (Function4<Seq<String>, BaseDateTimeType, Seq<Base>, Map<String, String>, BaseDateTimeType>)
            (path, date, parents, context) -> shiftDate(date, provider));

    return registry;
  }

  /**
   * Generates a tID for the date value, adds extension with tID, and nulls the original value.
   *
   * @param date the date element to process
   * @param provider the provider that generates and caches date tIDs
   * @return the modified date element
   */
  static BaseDateTimeType shiftDate(BaseDateTimeType date, GeneratingReplacementProvider provider) {
    if (date != null && date.getValue() != null) {
      var dateValue = date.getValueAsString();
      var tId = provider.generateDateTid(dateValue);
      date.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType(tId));
      date.setValue(null);
    }
    return date;
  }

  static Bundle deidentify(
      Config config,
      Registry registry,
      Bundle bundle,
      String patientIdentifier,
      MeterRegistry meterRegistry) {
    var sample = Timer.start(meterRegistry);

    Map<String, String> staticContext =
        new Map.Map1<>(Handlers.patientIdentifierKey(), patientIdentifier);
    Deidentifhir deidentifhir = Deidentifhir.apply(config, registry);
    var deidentified = (Bundle) deidentifhir.deidentify(bundle, staticContext);
    sample.stop(meterRegistry.timer("deidentify"));
    return deidentified;
  }
}
