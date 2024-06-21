package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.time.Duration;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import scala.Function4;
import scala.collection.immutable.Map;
import scala.collection.immutable.Seq;

public class DeidentifhirUtils {
  public static Registry generateRegistry(
      String patientId, java.util.Map<String, String> transportIds, Duration dateShiftValue) {
    var keyCreator = NamespacingReplacementProvider.withNamespacing(patientId);
    var replacementProvider = NamespacingReplacementProvider.of(keyCreator, transportIds);
    DateShiftingProvider dsp = new DateShiftingProvider(dateShiftValue);

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
    registry.addHander(
        "shiftDateHandler", JavaCompat.partiallyApply(dsp, Handlers::shiftDateHandler));
    return registry;
  }

  public static Bundle deidentify(
      Config config, Registry registry, Bundle bundle, String patientId) {
    Map<String, String> staticContext = new Map.Map1<>(Handlers.patientIdentifierKey(), patientId);
    Deidentifhir deidentifhir = Deidentifhir.apply(config, registry);
    return (Bundle) deidentifhir.deidentify(bundle, staticContext);
  }
}
