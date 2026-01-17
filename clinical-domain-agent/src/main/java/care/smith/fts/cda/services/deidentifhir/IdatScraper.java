package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.util.Set;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import scala.Function4;
import scala.collection.immutable.Map;
import scala.collection.immutable.Seq;

public class IdatScraper {
  private final Deidentifhir deidentiFHIR;
  private final ScrapingStorage scrapingStorage;

  public IdatScraper(Config config, ConsentedPatient patient) {
    var keyCreator = NamespacingReplacementProvider.withNamespacing(patient.id());
    scrapingStorage = new ScrapingStorage(keyCreator);

    Registry registry = new Registry();

    var idHandler = JavaCompat.partiallyApply(scrapingStorage, Handlers::idReplacementHandler);
    registry.addHander("idReplacementHandler", idHandler);

    var refHandler =
        JavaCompat.partiallyApply(scrapingStorage, Handlers::referenceReplacementHandler);
    registry.addHander("referenceReplacementHandler", refHandler);

    var identifierHandler =
        JavaCompat.partiallyApply2(
            scrapingStorage, true, Handlers::identifierValueReplacementHandler);
    registry.addHander("identifierValueReplacementHandler", identifierHandler);

    var conditionalRefHandler =
        JavaCompat.partiallyApply2(
            scrapingStorage, scrapingStorage, Handlers::conditionalReferencesReplacementHandler);
    registry.addHander("conditionalReferencesReplacementHandler", conditionalRefHandler);

    // No-op handlers for non-ID fields - return values unchanged since
    // IdatScraper only gathers IDs, not actual deidentification.
    Function4<Seq<String>, StringType, Seq<Base>, Map<String, String>, StringType> stringIdentity =
        (path, value, parents, context) -> value;
    registry.addHander("postalCodeHandler", stringIdentity);
    registry.addHander("PSEUDONYMISIERTstringReplacementHandler", stringIdentity);

    Function4<Seq<String>, DateType, Seq<Base>, Map<String, String>, DateType> dateIdentity =
        (path, date, parents, context) -> date;
    registry.addHander("generalizeDateHandler", dateIdentity);
    registry.addHander("shiftDateHandler", dateIdentity);

    deidentiFHIR = Deidentifhir.apply(config, registry);
  }

  /**
   * Gather all IDs contained in the provided bundle and return them as a Set.
   *
   * @return a Set of all IDs gathered in the Resource
   */
  public Set<String> gatherIDs(Resource resource) {
    deidentiFHIR.deidentify(resource);
    return scrapingStorage.getGatheredIdats();
  }
}
