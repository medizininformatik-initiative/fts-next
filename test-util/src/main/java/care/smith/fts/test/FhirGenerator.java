package care.smith.fts.test;

import static care.smith.fts.util.FhirUtils.stringToFhirBundle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;

/**
 * The FhirGenerator reads a template file from disk and replaces given word e.g. $PATIENT_ID
 * occurrences with a given Replacement e.g. an UUID
 */
@Slf4j
public class FhirGenerator {
  private final CharBuffer templateBuffer;
  private final HashMap<String, Replacement> replacements = new HashMap<>();

  public FhirGenerator(String templateFile) throws IOException {
    templateBuffer = TemplateLoader.getCharBuffer(templateFile, this.getClass().getClassLoader());
  }

  public void replaceTemplateFieldWith(String field, Replacement replaceWith) {
    replacements.put(field, replaceWith);
  }

  /**
   * If the template file is a valid JSON of a Fhir resource then generateBundle creates a
   * Collection Bundle with multiple resource.
   *
   * @param totalEntries the total number of entries in the bundle
   * @param pageSize the number of entries in this bundle page
   * @return
   */
  public Bundle generateBundle(int totalEntries, int pageSize) {
    Bundle bundle = new Bundle();
    bundle.setTotal(totalEntries);
    bundle.setType(Bundle.BundleType.COLLECTION);

    for (int i = 0; i < pageSize; i++) {
      bundle.addEntry(generateEntryComponent());
    }
    return bundle;
  }

  private Bundle.BundleEntryComponent generateEntryComponent() {
    return new Bundle.BundleEntryComponent().setResource(stringToFhirBundle(generateString()));
  }

  /**
   * Replaces the template fields
   *
   * @return The template file with replaced fields
   */
  public String generateString() {
    String s = templateBuffer.toString();
    for (Map.Entry<String, Replacement> m : replacements.entrySet()) {
      s = s.replace(m.getKey(), m.getValue().apply());
    }
    return s;
  }

  public InputStream generateInputStream() {
    return new ByteArrayInputStream(generateString().getBytes());
  }

  public interface Replacement {
    String apply();
  }

  public static class UUID implements Replacement {
    public String apply() {
      return java.util.UUID.randomUUID().toString();
    }
  }

  public static class Fixed implements Replacement {
    private final String value;

    public Fixed(String value) {
      this.value = value;
    }

    public String apply() {
      return value;
    }
  }
}