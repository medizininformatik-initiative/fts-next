package care.smith.fts.util;

import static java.util.stream.Stream.concat;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

@Slf4j
public class FhirUtils {
  public static final FhirContext fctx = FhirContext.forR4();

  /**
   * @param resource the FHIR resource
   * @return bundle as JSON string - not pretty printed
   */
  public static String fhirResourceToString(Resource resource) {
    return fctx.newJsonParser().encodeResourceToString(resource);
  }

  /**
   * @param bundleString the FHIR bundle as a string
   * @return the FHIR bundle
   */
  public static Bundle stringToFhirBundle(String bundleString) {
    return fctx.newJsonParser().parseResource(Bundle.class, bundleString);
  }

  /**
   * @param inputStream the FHIR bundle as a InputStream
   * @return the FHIR bundle
   */
  public static Bundle inputStreamToFhirBundle(InputStream inputStream) {
    return inputStreamToFhirResource(Bundle.class, inputStream);
  }

  /**
   * @param clazz the class of the resource type
   * @param inputStream the FHIR bundle as a InputStream
   * @return the FHIR bundle
   */
  public static <T extends IBaseResource> T inputStreamToFhirResource(
      Class<T> clazz, InputStream inputStream) {
    return fctx.newJsonParser().parseResource(clazz, inputStream);
  }

  /**
   * function to pretty print fhir data for debugging purposes
   *
   * @param prepend label for the output
   * @param resource the resource to output
   */
  public static void printJson(String prepend, Resource resource) {
    IParser parser = fctx.newJsonParser();

    String output = parser.setPrettyPrint(true).encodeResourceToString(resource);
    log.info(prepend + output);
  }

  /**
   * Convenience method to convert a byte array to a <code>BASE64</code> encoded {@link String}.
   *
   * @param bytes the bytes to encode
   * @return the encoded bytes
   */
  public static String convertBytesToString(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  /**
   * Convenience method to convert a <code>BASE64</code> byte array from a {@link String} to an
   * actual byte array.
   *
   * @param text the text to decode
   * @return the decoded text
   */
  public static byte[] convertStringToBytes(String text) {
    return Base64.getDecoder().decode(text);
  }

  public static Stream<Bundle.BundleEntryComponent> entryStream(Bundle bundle) {
    return bundle.getEntry().stream();
  }

  public static Stream<Resource> resourceStream(Bundle bundle) {
    return entryStream(bundle).map(Bundle.BundleEntryComponent::getResource);
  }

  public static Collector<? super Resource, List<Resource>, Bundle> toBundle() {
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
    return new Bundle().setEntry(list);
  }
}
