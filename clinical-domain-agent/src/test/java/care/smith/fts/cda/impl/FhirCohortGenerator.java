package care.smith.fts.cda.impl;

import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static java.lang.Math.ceilDiv;
import static java.time.ZoneId.systemDefault;
import static java.util.function.Function.identity;
import static org.hl7.fhir.r4.model.Bundle.BundleType.SEARCHSET;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Consent.ProvisionComponent;

public class FhirCohortGenerator {

  private final String pidSystem;
  private final String policySystem;
  private final Set<String> policies;
  private final AtomicInteger patientIndex;

  public FhirCohortGenerator(String pidSystem, String policySystem, Set<String> policies) {
    this.pidSystem = pidSystem;
    this.policySystem = policySystem;
    this.policies = policies;
    this.patientIndex = new AtomicInteger(1);
  }

  public Bundle generate() {
    return generate(1).findFirst().orElseThrow();
  }

  public Stream<Bundle> generate(int numPatients) {
    return generate(numPatients, 1);
  }

  public Stream<Bundle> generate(int numPatients, int numConsents) {
    return generate(numPatients, numConsents, numPatients);
  }

  /**
   * @param numPatients Number of patients to generate.
   * @param numConsents Number of consents to generate per patient.
   * @param pageSize Number of patients to include in each Bundle.
   * @return A stream of Bundles, each containing a page of patients with a single consent.
   */
  public Stream<Bundle> generate(int numPatients, int numConsents, int pageSize) {
    var numPages = ceilDiv(numPatients, pageSize);
    return IntStream.range(0, numPages)
        .mapToObj(index -> generatePage(numConsents, pageSize, numPages, index));
  }

  private Bundle generatePage(int numConsents, int pageSize, int numPages, int index) {
    var bundle =
        IntStream.range(0, pageSize)
            .mapToObj(i -> generatePatientWithConsents(numConsents))
            .flatMap(identity())
            .collect(toBundle())
            .setType(SEARCHSET);
    System.out.println(
        "generated page "
            + index
            + " of "
            + numPages
            + " with "
            + bundle.getEntry().size()
            + " entries");
    if (index < (numPages - 1)) {
      bundle.addLink().setRelation("next").setUrl("/Consent?_page=" + (index + 1));
    }
    return bundle;
  }

  private Stream<Resource> generatePatientWithConsents(int numConsents) {
    var patientId = String.valueOf(patientIndex.getAndIncrement());
    var patient = generatePatient(patientId, pidSystem);
    return Stream.concat(
        Stream.of(patient), Stream.generate(() -> generateConsent(patientId)).limit(numConsents));
  }

  private Consent generateConsent(String patientId) {
    var consent = new Consent();
    consent.setId("consent-" + patientId);
    consent.setProvision(generateProvision(policySystem, policies));
    consent.setPatient(generatePatientRef(patientId));
    return consent;
  }

  private static Reference generatePatientRef(String id) {
    return new Reference().setReference("Patient/patient-" + id);
  }

  private static ProvisionComponent generateProvision(String policySystem, Set<String> policies) {
    var coding = new Coding().setSystem(policySystem).setCode(policies.iterator().next());
    var concept = new CodeableConcept().addCoding(coding);
    var inner = new ProvisionComponent().setPeriod(generatePeriod()).addCode(concept);
    return new ProvisionComponent().addProvision(inner);
  }

  private static Period generatePeriod() {
    var now = LocalDateTime.now();

    var startDateTime = now.minusYears(3);
    var startDate = Date.from(startDateTime.atZone(systemDefault()).toInstant());

    var endDateTime = now.plusYears(2);
    var endDate = Date.from(endDateTime.atZone(systemDefault()).toInstant());

    return new Period().setStart(startDate).setEnd(endDate);
  }

  private static Patient generatePatient(String id, String pidSystem) {
    String pid = "patient-" + id;
    var patient = new Patient();
    patient.setId(pid);

    var patientId = new Identifier();
    patientId.setSystem(pidSystem);
    patientId.setValue(pid);

    return patient.setIdentifier(List.of(patientId));
  }
}
