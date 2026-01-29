package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.util.NanoIdUtils.nanoId;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
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
  private final Deidentifhir deidentiFHIR;
  private final ScrapingStorage scrapingStorage;
  private final java.util.Map<String, String> dateTransportMappings = new HashMap<>();

  public DataScraper(Config config, ConsentedPatient patient) {
    var patientId = patient.id();
    var keyCreator = NamespacingReplacementProvider.withNamespacing(patientId);
    scrapingStorage = new ScrapingStorage(keyCreator);

    var registry = new Registry();

    // ID gathering handlers - use same names as config file
    registry.addHander(
        "idReplacementHandler",
        JavaCompat.partiallyApply(scrapingStorage, Handlers::idReplacementHandler));
    registry.addHander(
        "referenceReplacementHandler",
        JavaCompat.partiallyApply(scrapingStorage, Handlers::referenceReplacementHandler));
    registry.addHander(
        "identifierValueReplacementHandler",
        JavaCompat.partiallyApply2(
            scrapingStorage, true, Handlers::identifierValueReplacementHandler));
    registry.addHander(
        "conditionalReferencesReplacementHandler",
        JavaCompat.partiallyApply2(
            scrapingStorage, scrapingStorage, Handlers::conditionalReferencesReplacementHandler));

    // No-op handlers for non-ID fields - return values unchanged since
    // DataScraper only gathers IDs and dates, not actual deidentification
    Function4<Seq<String>, StringType, Seq<Base>, Map<String, String>, StringType> stringIdentity =
        (path, value, parents, context) -> value;
    registry.addHander("postalCodeHandler", stringIdentity);
    registry.addHander("PSEUDONYMISIERTstringReplacementHandler", stringIdentity);

    Function4<Seq<String>, DateType, Seq<Base>, Map<String, String>, DateType> dateIdentity =
        (path, date, parents, context) -> date;
    registry.addHander("generalizeDateHandler", dateIdentity);

    // Date handler: generates tID per unique date value, stores tID→dateValue mapping
    // Note: Bundle modifications (extensions, null values) happen during deidentification
    registry.addHander(
        "shiftDateHandler",
        (Function4<Seq<String>, BaseDateTimeType, Seq<Base>, Map<String, String>, BaseDateTimeType>)
            this::gatherDate);

    deidentiFHIR = Deidentifhir.apply(config, registry);
  }

  /**
   * Scrapes the resource and returns gathered data.
   *
   * @param resource the FHIR resource to scrape
   * @return scraped IDs and tID→date mappings
   */
  public ScrapedData scrape(Resource resource) {
    deidentiFHIR.deidentify(resource);
    return new ScrapedData(
        Set.copyOf(scrapingStorage.getGatheredIdats()),
        java.util.Map.copyOf(dateTransportMappings));
  }

  private BaseDateTimeType gatherDate(
      Seq<String> path, BaseDateTimeType date, Seq<Base> parents, Map<String, String> context) {
    Optional.ofNullable(date)
        .map(BaseDateTimeType::getValue)
        .map(v -> date.getValueAsString())
        .filter(originalDate -> !dateTransportMappings.containsValue(originalDate))
        .ifPresent(originalDate -> dateTransportMappings.put(nanoId(), originalDate));
    return date;
  }

  public record ScrapedData(Set<String> ids, java.util.Map<String, String> dateTransportMappings) {}
}
