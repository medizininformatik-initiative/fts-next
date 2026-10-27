package care.smith.fts.util.fhir;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import ca.uhn.fhir.context.FhirContext;
import java.io.InputStream;
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

  static Collector<Resource, ?, Bundle> toBundle() {
    return collectingAndThen(toList(), FhirUtils::bundleOf);
  }

  /**
   * @param type the bundle type, e.g. that of the source bundle; {@code null} leaves it unset
   */
  static Collector<Resource, ?, Bundle> toBundle(Bundle.BundleType type) {
    return collectingAndThen(toBundle(), bundle -> bundle.setType(type));
  }

  private static Bundle bundleOf(List<Resource> resources) {
    var entries =
        resources.stream().map(r -> new Bundle.BundleEntryComponent().setResource(r)).toList();
    return new Bundle().setEntry(entries);
  }
}
