package care.smith.fts.cda.impl;

import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Stream.concat;

import care.smith.fts.util.ConsentedPatientExtractor;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

/**
 * The patients of a page of the patient search, combined with the result of the search for their
 * consents by identifier, which also holds the consents of other patient resources with the same
 * identifier, and these patient resources.
 *
 * @param patients the patients of the page
 * @param extractor reads the identifier by which the patients are merged
 */
record PatientPage(List<Patient> patients, ConsentedPatientExtractor extractor) {

  static PatientPage of(Bundle page, ConsentedPatientExtractor extractor) {
    return new PatientPage(typedResourceStream(page, Patient.class).toList(), extractor);
  }

  /** The identifiers of the patients by which they are merged, distinct. */
  List<String> identifiers() {
    return patients.stream().flatMap(p -> identifier(p).stream()).distinct().toList();
  }

  /**
   * The patients of the page with the patients and consents of the consent search. A patient of the
   * consent search is added only if it is not on the page and is merged with a patient of the page
   * by identifier; consents of other patients of the consent search are left out.
   *
   * @param consentPages the pages of the consent search for {@link #identifiers()}
   */
  Bundle withConsents(List<Bundle> consentPages) {
    var identifiers = Set.copyOf(identifiers());
    var byMerged =
        otherPatients(consentPages).collect(partitioningBy(p -> isMerged(p, identifiers)));
    var unmerged = byMerged.get(false).stream().map(Patient::getIdPart).collect(toSet());
    var consents =
        consentPages.stream()
            .flatMap(b -> typedResourceStream(b, Consent.class))
            .filter(c -> referencedPatientId(c).filter(unmerged::contains).isEmpty());
    return concat(concat(patients.stream(), byMerged.get(true).stream()), consents)
        .collect(toBundle());
  }

  /**
   * The id of the patient a consent literally references. Consents referencing their patient by
   * identifier only (logical reference) have none.
   */
  static Optional<String> referencedPatientId(Consent c) {
    return Optional.of(c.getPatient())
        .filter(Reference::hasReference)
        .map(r -> r.getReferenceElement().getIdPart());
  }

  /** The patients of the consent pages not on the page, each once. */
  private Stream<Patient> otherPatients(List<Bundle> consentPages) {
    var seen = new HashSet<String>();
    patients.forEach(p -> seen.add(p.getIdPart()));
    return consentPages.stream()
        .flatMap(b -> typedResourceStream(b, Patient.class))
        .filter(p -> seen.add(p.getIdPart()));
  }

  private boolean isMerged(Patient patient, Set<String> identifiers) {
    return identifier(patient).filter(identifiers::contains).isPresent();
  }

  private Optional<String> identifier(Patient patient) {
    return extractor.patientIdentifier(Stream.of(patient).collect(toBundle()));
  }
}
