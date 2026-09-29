package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;

import care.smith.fts.deidentifier.Registry;
import care.smith.fts.deidentifier.allowlist.AllowList;
import care.smith.fts.deidentifier.handlers.Handlers;
import com.typesafe.config.Config;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Optional;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.IdType;
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
    registry.addHandler(
        "idReplacementHandler", IdType.class, Handlers.idReplacementHandler(provider));
    registry.addHandler(
        "referenceReplacementHandler",
        StringType.class,
        Handlers.referenceReplacementHandler(provider, provider));
    registry.addAlias("conditionalReferencesReplacementHandler", "referenceReplacementHandler");
    registry.addHandler(
        "identifierValueReplacementHandler",
        StringType.class,
        Handlers.identifierValueReplacementHandler(provider, true));
    registry.addHandler("generalizeDateHandler", DateType.class, Handlers::generalizeDateHandler);
    registry.addHandler("postalCodeHandler", StringType.class, Handlers::generalizePostalCode);
    registry.addHandler(
        "PSEUDONYMISIERTstringReplacementHandler",
        StringType.class,
        Handlers.stringReplacementHandler("PSEUDONYMISIERT"));
    // the shift removes the value, so no handler may run after it
    registry.addTerminalHandler(
        "shiftDateHandler",
        BaseDateTimeType.class,
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
        AllowList.fromConfig(config, registry).deidentifyBundle(bundle, patientIdentifier);
    sample.stop(meterRegistry.timer("deidentify"));
    return deidentified;
  }
}
