package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Resource;
import scala.Function4;
import scala.collection.immutable.Map;
import scala.collection.immutable.Seq;

/**
 * Scrapes IDs and dates from FHIR resources based on deidentifhir configuration. Gathers both
 * identifying data (IDs, references, identifiers) and date values from configured paths.
 *
 * <p>For dates, generates transport IDs (tIDs), attaches them as extensions to the date elements,
 * nulls the original date values, and returns tID→originalDate mappings for TCA processing.
 */
public class DataScraper {
  public static final String DATE_SHIFT_EXTENSION_URL =
      "https://fts.smith.care/fhir/StructureDefinition/date-shift-transport-id";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int TRANSPORT_ID_BYTES = 16;

  private final Deidentifhir deidentiFHIR;
  private final ScrapingStorage scrapingStorage;
  private final java.util.Map<String, String> dateTransportMappings = new HashMap<>();
  private final String patientId;

  public DataScraper(Config config, ConsentedPatient patient) {
    this.patientId = patient.id();
    var keyCreator = NamespacingReplacementProvider.withNamespacing(patientId);
    scrapingStorage = new ScrapingStorage(keyCreator);

    Registry registry = new Registry();

    // ID gathering handlers
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

    // Date handler: generates tID per unique date value, stores tID→dateValue mapping
    // Note: Bundle modifications (extensions, null values) happen during deidentification
    registry.addHander(
        "gatherDateHandler",
        (Function4<Seq<String>, BaseDateTimeType, Seq<Base>, Map<String, String>, BaseDateTimeType>)
            (path, date, parents, context) -> {
              Optional.ofNullable(date)
                  .map(BaseDateTimeType::getValue)
                  .map(v -> date.getValueAsString())
                  .filter(originalDate -> !dateTransportMappings.containsValue(originalDate))
                  .ifPresent(
                      originalDate ->
                          dateTransportMappings.put(generateTransportId(), originalDate));
              return date;
            });

    deidentiFHIR = Deidentifhir.apply(config, registry);
  }

  /**
   * Scrapes the resource and returns gathered data.
   *
   * @param resource the FHIR resource to scrape
   * @return scraped IDs and tID→date mappings
   */
  public ScrapedData scrape(Resource resource) {
    scrapingStorage.getGatheredIdats().clear();
    dateTransportMappings.clear();
    deidentiFHIR.deidentify(resource);
    return new ScrapedData(
        scrapingStorage.getGatheredIdats(), new HashMap<>(dateTransportMappings));
  }

  private static String generateTransportId() {
    byte[] bytes = new byte[TRANSPORT_ID_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public record ScrapedData(Set<String> ids, java.util.Map<String, String> dateTransportMappings) {}
}
