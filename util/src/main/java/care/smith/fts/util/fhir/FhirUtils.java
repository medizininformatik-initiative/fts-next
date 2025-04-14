package care.smith.fts.util.fhir;

import static java.util.stream.Stream.concat;

import ca.uhn.fhir.context.FhirContext;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Stream;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

public interface FhirUtils {
  FhirContext fctx = FhirContext.forR4();

  /**
   * @param resource the FHIR resource
   * @return bundle as JSON string - not pretty printed
   */
  static String fhirResourceToString(Resource resource) {
    return fctx.newJsonParser().encodeResourceToString(resource);
  }

  /**
   * @param bundleString the FHIR bundle as a string
   * @return the FHIR bundle
   */
  static Bundle stringToFhirBundle(String bundleString) {
    return fctx.newJsonParser().parseResource(Bundle.class, bundleString);
  }

  /**
   * @param clazz the class of the resource type
   * @param string the FHIR resource as a string
   * @return the FHIR resource
   */
  static <T extends Resource> T stringToFhirResource(Class<T> clazz, String string) {
    return fctx.newJsonParser().parseResource(clazz, string);
  }

  /**
   * @param clazz the class of the resource type
   * @param inputStream the FHIR bundle as a InputStream
   * @return the FHIR bundle
   */
  static <T extends IBaseResource> T inputStreamToFhirResource(
      Class<T> clazz, InputStream inputStream) {
    return fctx.newJsonParser().parseResource(clazz, inputStream);
  }

  static Stream<Bundle.BundleEntryComponent> entryStream(Bundle bundle) {
    return bundle.getEntry().stream();
  }

  static Stream<Resource> resourceStream(Bundle bundle) {
    return entryStream(bundle).map(Bundle.BundleEntryComponent::getResource);
  }

  static <T> Stream<T> typedResourceStream(Bundle bundle, Class<T> type) {
    return resourceStream(bundle).filter(type::isInstance).map(type::cast);
  }

  static Collector<? super Resource, List<Resource>, Bundle> toBundle() {
    return Collector.of(
        ArrayList::new,
        List::add,
        (l1, l2) -> concat(l1.stream(), l2.stream()).toList(),
        FhirUtils::toBundle,
        Collector.Characteristics.UNORDERED);
  }

  private static Bundle toBundle(List<Resource> l) {
    List<Bundle.BundleEntryComponent> list =
        l.stream().map(r -> new Bundle.BundleEntryComponent().setResource(r)).toList();
    return new Bundle().setTotal(l.size()).setEntry(list);
  }
}
