package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.tca.TransportIDs;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.time.Duration;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Resource;
import scala.Function4;
import scala.collection.immutable.Map;
import scala.collection.immutable.Seq;

public class DeidentifhirService {
  private final Deidentifhir deidentifhir;
  private final ConsentedPatient patient;

  public DeidentifhirService(
      Config config, ConsentedPatient patient, TransportIDs transportIds, Duration dateShiftValue) {
    this.patient = patient;
    NamespacingService namespacingService = new NamespacingService(patient.id());
    DateShiftingProvider dsp = new DateShiftingProvider(dateShiftValue);
    PseudonymProvider ths = new PseudonymProvider(namespacingService, transportIds);

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
        "idReplacementHandler", JavaCompat.partiallyApply(ths, Handlers::idReplacementHandler));
    registry.addHander(
        "referenceReplacementHandler",
        JavaCompat.partiallyApply(ths, Handlers::referenceReplacementHandler));
    registry.addHander(
        "identifierValueReplacementHandler",
        JavaCompat.partiallyApply2(ths, true, Handlers::identifierValueReplacementHandler));
    registry.addHander(
        "conditionalReferencesReplacementHandler",
        JavaCompat.partiallyApply2(ths, ths, Handlers::conditionalReferencesReplacementHandler));
    registry.addHander(
        "shiftDateHandler", JavaCompat.partiallyApply(dsp, Handlers::shiftDateHandler));

    deidentifhir = Deidentifhir.apply(config, registry);
  }

  public Resource deidentify(Resource resource) {
    scala.collection.immutable.Map<String, String> staticContext =
        new Map.Map1<>(Handlers.patientIdentifierKey(), patient.id());
    return deidentifhir.deidentify(resource, staticContext);
  }
}
