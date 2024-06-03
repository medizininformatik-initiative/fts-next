package care.smith.fts.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;

import java.io.InputStream;
import java.util.Base64;

@Slf4j
public class FhirUtils {
    public static final FhirContext fctx = FhirContext.forR4();

    /**
     * @param bundle the FHIR bundle
     * @return bundle as JSON string - not pretty printed
     */
    public static String fhirBundleToString(Bundle bundle) {
        return fctx.newJsonParser().encodeResourceToString(bundle);
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
        return fctx.newJsonParser().parseResource(Bundle.class, inputStream);
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

}
