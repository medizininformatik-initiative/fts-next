package care.smith.fts.util.fhir;

import static care.smith.fts.util.fhir.FhirUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hl7.fhir.r4.model.Bundle.BundleType.COLLECTION;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collector.Characteristics;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

class FhirUtilsTest {

  @Test
  void serializeWithFhirResourceToString() {
    var ser = fhirResourceToString(new Bundle());
    assertThat(ser).isEqualTo("{\"resourceType\":\"Bundle\"}");
  }

  @Test
  void deserializeWithStringToFhirBundle() {
    var bundle = new Bundle();
    var deBundle = stringToFhirBundle(fhirResourceToString(bundle));
    assertThat(deBundle).matches(b -> b.equalsDeep(bundle));
  }

  @Test
  void inputStreamToBundle() {
    var bundle = new Bundle();
    var byteArrayInputStream = new ByteArrayInputStream(fhirResourceToString(bundle).getBytes());
    var fromStreamBundle = inputStreamToFhirResource(Bundle.class, byteArrayInputStream);
    assertThat(fromStreamBundle).matches(b -> b.equalsDeep(bundle));
  }

  @Test
  void deserializeStringToFhirResource() {
    var bundle = new Bundle();
    var s = fhirResourceToString(bundle);
    assertThat(stringToFhirResource(Bundle.class, s)).matches(b -> b.equalsDeep(bundle));
  }

  @Test
  void testEntryStream() {
    var bundle = new Bundle();
    bundle.addEntry(new BundleEntryComponent());
    bundle.addEntry(new BundleEntryComponent());
    bundle.addEntry(new BundleEntryComponent());

    Stream<BundleEntryComponent> stream = entryStream(bundle);
    assertThat(stream).isNotNull();

    List<BundleEntryComponent> entries = stream.collect(Collectors.toList());
    assertThat(bundle.getEntry()).isEqualTo(entries);
  }

  @Test
  void streamWithoutResource() {
    List<BundleEntryComponent> entries = List.of();
    Bundle b = new Bundle().setEntry(entries);

    Bundle streamAndCollect = resourceStream(b).collect(toBundle());
    assertThat(streamAndCollect.getEntry()).map(BundleEntryComponent::getResource).isEmpty();
  }

  @Test
  void streamWithOneResource() {
    Patient resource = new Patient();
    List<BundleEntryComponent> entries =
        Stream.of(resource).map(r -> new BundleEntryComponent().setResource(r)).toList();
    Bundle b = new Bundle().setEntry(entries);

    Bundle streamAndCollect = resourceStream(b).collect(toBundle());
    assertThat(streamAndCollect.getEntry())
        .map(BundleEntryComponent::getResource)
        .containsExactlyInAnyOrder(resource);
  }

  @Test
  void streamWithMultipleResources() {
    Patient patient = new Patient();
    Observation observation = new Observation();
    List<BundleEntryComponent> entries =
        Stream.of(patient, observation)
            .map(r -> new BundleEntryComponent().setResource(r))
            .toList();
    Bundle b = new Bundle().setEntry(entries);

    Bundle streamAndCollect = resourceStream(b).collect(toBundle());
    assertThat(streamAndCollect.getEntry())
        .map(BundleEntryComponent::getResource)
        .containsExactlyInAnyOrder(patient, observation);
  }

  @Test
  public void testTypedResourceStream() {
    var bundle = new Bundle();

    bundle.addEntry().setResource(new Patient());
    bundle.addEntry().setResource(new Observation());
    bundle.addEntry().setResource(new Patient());
    Stream<Patient> patientStream = typedResourceStream(bundle, Patient.class);

    List<Patient> patients = patientStream.toList();
    assertThat(patients).isNotNull();
    assertThat(patients).hasSize(2);
    assertThat(patients).allMatch(Objects::nonNull);
  }

  @Test
  public void toBundleCollectorSimple() {
    var resources = List.of(new Patient(), new Patient(), new Patient());
    Bundle bundle = resources.stream().collect(toBundle());
    assertThat(bundle).isNotNull();
    assertThat(bundle.getEntry()).hasSize(3);
  }

  @Test
  void toBundleKeepsEncounterOrderOfParallelStream() {
    var resources =
        IntStream.range(0, 10_000).mapToObj(i -> (Resource) new Patient().setId("p" + i)).toList();

    var bundle = resources.parallelStream().collect(toBundle());

    assertThat(bundle.getEntry())
        .map(BundleEntryComponent::getResource)
        .containsExactlyElementsOf(resources);
  }

  @Test
  void toBundleIsOrdered() {
    assertThat(toBundle().characteristics()).doesNotContain(Characteristics.UNORDERED);
  }

  @Test
  void toBundleSetsNoType() {
    var bundle = Stream.of(new Patient()).collect(toBundle());

    assertThat(bundle.hasType()).isFalse();
  }

  @Test
  void toBundleWithTypeSetsType() {
    var bundle = Stream.of(new Patient()).collect(toBundle(COLLECTION));

    assertThat(bundle.getType()).isEqualTo(COLLECTION);
  }

  @Test
  void toBundleWithoutTypeOfSourceSetsNoType() {
    var source = new Bundle();

    var bundle = Stream.of(new Patient()).collect(toBundle(source.getType()));

    assertThat(bundle.hasType()).isFalse();
  }

  @Test
  void toBundleSetsNoTotal() {
    var bundle = Stream.of(new Patient(), new Patient()).collect(toBundle());

    assertThat(bundle.hasTotal()).isFalse();
  }
}
