package care.smith.fts.test;

import static care.smith.fts.util.FhirUtils.toBundle;
import static java.lang.Math.min;

import care.smith.fts.util.FhirUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;

/**
 * The FhirGenerator reads a template file from disk and replaces given word e.g. $PATIENT_ID
 * occurrences with a given Replacement e.g. an UUID
 */
@Slf4j
public class FhirGenerator {
  private final CharBuffer templateBuffer;
  private final Map<String, Supplier<String>> inputReplacements = new HashMap<>();

  private FhirGenerator(String templateFile) throws IOException {
    templateBuffer = TemplateLoader.getCharBuffer(templateFile, this.getClass().getClassLoader());
  }

  public static FhirGenerator patient(
      Supplier<String> patientId, Supplier<String> identifierSystem, Supplier<String> year)
      throws IOException {
    var generator = new FhirGenerator("PatientTemplate.json");
    generator.replaceTemplateFieldWith("$PATIENT_ID", patientId);
    generator.replaceTemplateFieldWith("$IDENTIFIER_SYSTEM", identifierSystem);
    generator.replaceTemplateFieldWith("$YEAR", year);
    return generator;
  }

  public static FhirGenerator gicsResponse(
      Supplier<String> questionnaireResponseId, Supplier<String> patientId) throws IOException {
    var generator = new FhirGenerator("GicsResponseTemplate.json");
    generator.replaceTemplateFieldWith("$QUESTIONNAIRE_RESPONSE_ID", questionnaireResponseId);
    generator.replaceTemplateFieldWith("$PATIENT_ID", patientId);
    return generator;
  }

  public static FhirGenerator resolveSearchResponse(
      Supplier<String> patientId, Supplier<String> hdsId) throws IOException {
    var generator = new FhirGenerator("FhirResolveSearchResponseTemplate.json");
    generator.replaceTemplateFieldWith("$PATIENT_ID", patientId);
    generator.replaceTemplateFieldWith("$HDS_ID", hdsId);
    return generator;
  }

  public static FhirGenerator transportBundle() throws IOException {
    return new FhirGenerator("TransportBundleTemplate.json");
  }

  public static FhirGenerator gpasGetOrCreateResponse(
      Supplier<String> original, Supplier<String> pseudonym) throws IOException {
    FhirGenerator generator = new FhirGenerator("gpas-get-or-create-response.json");
    generator.replaceTemplateFieldWith("$ORIGINAL", original);
    generator.replaceTemplateFieldWith("$PSEUDONYM", pseudonym);
    return generator;
  }

  public void replaceTemplateFieldWith(String field, Supplier<String> replaceWith) {
    inputReplacements.put(field, replaceWith);
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
    return Stream.generate(this::generateString)
        .limit(min(pageSize, totalEntries))
        .map(FhirUtils::stringToFhirBundle)
        .collect(toBundle())
        .setTotal(totalEntries);
  }

  /**
   * Generates the fhir resource using the template and parses it to resource
   *
   * @param clazz the resource type for parsing
   * @return
   */
  public <T extends IBaseResource> T generateResource(Class<T> clazz) {
    return FhirUtils.stringToFhirResource(clazz, this.generateString());
  }

  /**
   * Replaces the template fields
   *
   * @return The template file with replaced fields
   */
  public String generateString() {
    String s = templateBuffer.toString();
    for (Map.Entry<String, Supplier<String>> m : inputReplacements.entrySet()) {
      s = s.replace(m.getKey(), m.getValue().get());
    }
    return s;
  }

  public InputStream generateInputStream() {
    return new ByteArrayInputStream(generateString().getBytes());
  }

  public static class Incrementing implements Supplier<String> {
    private final String prefix;
    private long index;

    private Incrementing(String prefix, long index) {
      this.prefix = prefix;
      this.index = index;
    }

    public static Incrementing withPrefix(String prefix) {
      return new Incrementing(prefix, 0);
    }

    @Override
    public String get() {
      String s = prefix + index;
      index += 1;
      return s;
    }
  }
}
