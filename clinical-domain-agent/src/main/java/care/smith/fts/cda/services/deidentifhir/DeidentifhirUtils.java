package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;

import care.smith.fts.deidentifhir.Deidentifhir;
import care.smith.fts.deidentifhir.DeidentifhirHandler;
import care.smith.fts.deidentifhir.Registry;
import care.smith.fts.deidentifhir.handlers.Handlers;
import com.typesafe.config.Config;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.StringType;

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
    registry.addHandler("idReplacementHandler", Handlers.idReplacementHandler(provider));
    registry.addHandler(
        "referenceReplacementHandler", Handlers.referenceReplacementHandler(provider, provider));
    registry.addAlias("conditionalReferencesReplacementHandler", "referenceReplacementHandler");
    registry.addHandler(
        "identifierValueReplacementHandler",
        Handlers.identifierValueReplacementHandler(provider, true));
    registry.addHandler(
        "generalizeDateHandler", (DeidentifhirHandler<DateType>) Handlers::generalizeDateHandler);
    registry.addHandler(
        "postalCodeHandler", (DeidentifhirHandler<StringType>) Handlers::generalizePostalCode);
    registry.addHandler(
        "PSEUDONYMISIERTstringReplacementHandler",
        Handlers.stringReplacementHandler("PSEUDONYMISIERT"));
    registry.addHandler(
        "shiftDateHandler",
        (DeidentifhirHandler<BaseDateTimeType>)
            (path, date, context) -> Optional.of(shiftDate(date, provider)));
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
    var deidentified =
        Deidentifhir.fromConfig(config, registry).deidentifyBundle(bundle, patientIdentifier);
    sample.stop(meterRegistry.timer("deidentify"));
    return deidentified;
  }
}
