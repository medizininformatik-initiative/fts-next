package care.smith.fts.cda.impl.cohort_selector;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Consent.ProvisionComponent;

public interface FhirCohortGenerator {

  static Bundle OnePatientWithConsent(String pidSystem, String policySystem, Set<String> policies) {
    return PatientsWithConsent(pidSystem, policySystem, policies, 1);
  }

  static Bundle PatientsWithMultipleConsents(
      String pidSystem, String policySystem, Set<String> policies, int n, int nConsent) {
    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.SEARCHSET);
    IntStream.range(0, n)
        .forEach(
            i ->
                addPatientWithMultipleConsents(
                    bundle, policySystem, policies, pidSystem, i, nConsent, 0));
    return bundle;
  }

  static Stream<Bundle> PatientsWithMultipleConsentsPaged(
      String pidSystem,
      String policySystem,
      Set<String> policies,
      int nPatients,
      int nConsents,
      int pageSize) {
    if (nPatients % pageSize != 0) {
      throw new IllegalArgumentException();
    }

    int pages = nPatients / pageSize;
    return IntStream.range(0, pages)
        .mapToObj(
            pageIndex -> {
              var bundle = new Bundle();
              bundle.setType(Bundle.BundleType.SEARCHSET);

              int offset = pageIndex * pageSize;
              IntStream.range(offset, offset + pageSize)
                  .forEach(
                      i ->
                          addPatientWithMultipleConsents(
                              bundle, policySystem, policies, pidSystem, i, nConsents, offset));
              if (pageIndex != pages - 1) {
                bundle.addLink().setRelation("next").setUrl("/Consent?_page=" + (pageIndex + 2));
              }
              return bundle;
            });
  }

  static Bundle PatientsWithConsent(
      String pidSystem, String policySystem, Set<String> policies, int n) {
    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.SEARCHSET);
    IntStream.range(0, n)
        .forEach(i -> addConsentPatientPair(bundle, policySystem, policies, pidSystem, i, 0));
    return bundle;
  }

  private static void addConsentPatientPair(
      Bundle bundle,
      String policySystem,
      Set<String> policies,
      String pidSystem,
      int index,
      int offset) {
    bundle.addEntry().setResource(generateConsent(policySystem, policies, index, offset));
    bundle.addEntry().setResource(generatePatient(pidSystem, index));
  }

  private static void addPatientWithMultipleConsents(
      Bundle bundle,
      String policySystem,
      Set<String> policies,
      String pidSystem,
      int pIndex,
      int nConsents,
      int offset) {
    bundle.addEntry().setResource(generatePatient(pidSystem, pIndex));
    IntStream.range(0, nConsents)
        .forEach(
            i -> bundle.addEntry().setResource(generateConsent(policySystem, policies, i, offset)));
  }

  private static Consent generateConsent(
      String policySystem, Set<String> policies, int i, int offset) {
    var consent = new Consent();
    consent.setId("consent-" + (i + offset));
    setProvision(policySystem, policies, consent);
    setPatientRef(consent, i + offset);
    return consent;
  }

  private static void setPatientRef(Consent consent, int i) {
    consent.setPatient(new Reference().setReference("Patient/patient-" + i));
  }

  private static void setProvision(String policySystem, Set<String> policies, Consent consent) {
    var provisionComponent =
        new ProvisionComponent()
            .addProvision(
                new ProvisionComponent()
                    .setPeriod(generatePeriod())
                    .addCode(
                        new CodeableConcept()
                            .addCoding(
                                new Coding()
                                    .setSystem(policySystem)
                                    .setCode(policies.iterator().next()))));
    consent.setProvision(provisionComponent);
  }

  private static Period generatePeriod() {
    var dates = generateDates();
    return new Period().setStart(dates.startDate()).setEnd(dates.endDate());
  }

  private static Dates generateDates() {
    var now = LocalDateTime.now();
    var startDateTime = now.minusYears(3);
    var startDate = Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant());
    var endDateTime = now.plusYears(2);
    var endDate = Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant());
    return new Dates(startDate, endDate);
  }

  record Dates(Date startDate, Date endDate) {}

  private static Patient generatePatient(String pidSystem, int i) {
    String pid = "patient-" + i;
    var patient = new Patient();
    patient.setId(pid);
    var patientId = new Identifier();
    patientId.setSystem(pidSystem);
    patientId.setValue("patient" + i);
    patient.setIdentifier(List.of(patientId));
    return patient;
  }
}
