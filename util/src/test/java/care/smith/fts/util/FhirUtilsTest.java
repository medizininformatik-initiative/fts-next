package care.smith.fts.util;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class FhirUtilsTest {

  @Test
  void testStreamWithoutResource() {
    List<Bundle.BundleEntryComponent> entries = List.of();
    Bundle b = new Bundle().setEntry(entries);

    Bundle streamAndCollect = resourceStream(b).collect(toBundle());
    assertThat(streamAndCollect.getEntry()).map(Bundle.BundleEntryComponent::getResource).isEmpty();
  }

  @Test
  void testStreamWithOneResource() {
    Patient resource = new Patient();
    List<Bundle.BundleEntryComponent> entries =
        Stream.of(resource).map(r -> new Bundle.BundleEntryComponent().setResource(r)).toList();
    Bundle b = new Bundle().setEntry(entries);

    Bundle streamAndCollect = resourceStream(b).collect(toBundle());
    assertThat(streamAndCollect.getEntry())
        .map(Bundle.BundleEntryComponent::getResource)
        .containsExactlyInAnyOrder(resource);
  }

  @Test
  void testStreamWithMultipleResources() {
    Patient patient = new Patient();
    Observation observation = new Observation();
    List<Bundle.BundleEntryComponent> entries =
        Stream.of(patient, observation)
            .map(r -> new Bundle.BundleEntryComponent().setResource(r))
            .toList();
    Bundle b = new Bundle().setEntry(entries);

    Bundle streamAndCollect = resourceStream(b).collect(toBundle());
    assertThat(streamAndCollect.getEntry())
        .map(Bundle.BundleEntryComponent::getResource)
        .containsExactlyInAnyOrder(patient, observation);
  }
}
