package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.util.Set;
import org.hl7.fhir.r4.model.Resource;

public class IDATScraper {
  private final Deidentifhir deidentiFHIR;
  private final ScrapingStorage scrapingStorage;

  public IDATScraper(Config config, ConsentedPatient patient) {
    var keyCreator = NamespacingReplacementProvider.withNamespacing(patient.id());
    scrapingStorage = new ScrapingStorage(keyCreator);

    Registry registry = new Registry();
    registry.addHander(
        "gatherIdHandler",
        JavaCompat.partiallyApply(scrapingStorage, Handlers::idReplacementHandler));
    registry.addHander(
        "gatherReferenceHandler",
        JavaCompat.partiallyApply(scrapingStorage, Handlers::referenceReplacementHandler));
    registry.addHander(
        "gatherIdentifierValueHandler",
        JavaCompat.partiallyApply2(
            scrapingStorage, true, Handlers::identifierValueReplacementHandler));
    registry.addHander(
        "gatherConditionalReferencesHandler",
        JavaCompat.partiallyApply2(
            scrapingStorage, scrapingStorage, Handlers::conditionalReferencesReplacementHandler));

    deidentiFHIR = Deidentifhir.apply(config, registry);
  }

  /**
   * Gather all IDs contained in the provided bundle and return them as a Set.
   *
   * @return a Set of all IDs gathered in the Resource
   */
  public Set<String> gatherIDs(Resource resource) {
    deidentiFHIR.deidentify(resource);
    return scrapingStorage.getGatheredIDATs();
  }
}
