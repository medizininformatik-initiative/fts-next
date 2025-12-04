package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider;
import com.typesafe.config.Config;
import de.ume.deidentifhir.Deidentifhir;
import de.ume.deidentifhir.Registry;
import de.ume.deidentifhir.util.Handlers;
import de.ume.deidentifhir.util.JavaCompat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

@Slf4j
public class IdatScraper {
  private final Deidentifhir deidentiFHIR;
  private final ScrapingStorage scrapingStorage;
  private final CompartmentMembershipChecker compartmentChecker;
  private final String patientIdentifier;
  private final String patientResourceId;
  private final boolean enableCompartmentNamespacing;

  public IdatScraper(
      Config config,
      ConsentedPatient patient,
      CompartmentMembershipChecker compartmentChecker,
      String patientResourceId,
      boolean enableCompartmentNamespacing) {
    this.compartmentChecker = compartmentChecker;
    this.patientIdentifier = patient.id();
    this.patientResourceId = patientResourceId;
    this.enableCompartmentNamespacing = enableCompartmentNamespacing;

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
   * <p>Resources in the patient compartment will have IDs prefixed with the patient ID. Resources
   * not in the compartment will have IDs without the patient prefix.
   *
   * @return a Set of all IDs gathered in the Resource
   */
  public Set<String> gatherIDs(Bundle bundle) {
    // Pre-compute compartment membership for all resources
    Map<String, Boolean> membership = precomputeCompartmentMembership(bundle);
    scrapingStorage.setCompartmentMembership(membership);

    deidentiFHIR.deidentify(bundle);
    return scrapingStorage.getGatheredIdats();
  }

  private Map<String, Boolean> precomputeCompartmentMembership(Bundle bundle) {
    if (!enableCompartmentNamespacing) {
      // When disabled, return empty map - ScrapingStorage defaults to all-in-compartment
      log.trace("Compartment namespacing disabled, treating all resources as in-compartment");
      return Map.of();
    }

    Map<String, Boolean> membership = new HashMap<>();

    log.trace(
        "Checking compartment membership with patientResourceId: {} for patient identifier: {}",
        patientResourceId,
        patientIdentifier);

    for (var entry : bundle.getEntry()) {
      Resource r = entry.getResource();
      String key = r.fhirType() + ":" + r.getIdPart();
      boolean inCompartment = compartmentChecker.isInPatientCompartment(r, patientResourceId);
      membership.put(key, inCompartment);
    }

    return membership;
  }
}
