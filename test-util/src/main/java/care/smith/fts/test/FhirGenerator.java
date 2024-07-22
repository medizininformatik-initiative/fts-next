package care.smith.fts.test;

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
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;

/**
 * The FhirGenerator reads a template file from disk and replaces given word e.g. $PATIENT_ID
 * occurrences with a given Replacement e.g. an UUID
 */
@Slf4j
public class FhirGenerator<T extends Resource> {
  private final Class<T> resourceType;
  private final CharBuffer templateBuffer;
  private final Map<String, Supplier<String>> inputReplacements = new HashMap<>();

  private FhirGenerator(Class<T> resourceType, String templateFile) throws IOException {
    templateBuffer = TemplateLoader.getCharBuffer(templateFile, this.getClass().getClassLoader());
    this.resourceType = resourceType;
  }

  public static FhirGenerator<Bundle> patient(
      Supplier<String> patientId, Supplier<String> identifierSystem, Supplier<String> year)
      throws IOException {
    var generator = new FhirGenerator<>(Bundle.class, "PatientTemplate.json");
    generator.replaceTemplateFieldWith("$PATIENT_ID", patientId);
    generator.replaceTemplateFieldWith("$IDENTIFIER_SYSTEM", identifierSystem);
    generator.replaceTemplateFieldWith("$YEAR", year);
    return generator;
  }

  public static FhirGenerator<Bundle> gicsResponse(
      Supplier<String> questionnaireResponseId, Supplier<String> patientId) throws IOException {
    var generator = new FhirGenerator<>(Bundle.class, "GicsResponseTemplate.json");
    generator.replaceTemplateFieldWith("$QUESTIONNAIRE_RESPONSE_ID", questionnaireResponseId);
    generator.replaceTemplateFieldWith("$PATIENT_ID", patientId);
    return generator;
  }

  public static FhirGenerator<Bundle> resolveSearchResponse(
      Supplier<String> patientId, Supplier<String> hdsId) throws IOException {
    var generator = new FhirGenerator<>(Bundle.class, "FhirResolveSearchResponseTemplate.json");
    generator.replaceTemplateFieldWith("$PATIENT_ID", patientId);
    generator.replaceTemplateFieldWith("$HDS_ID", hdsId);
    return generator;
  }

  public static FhirGenerator<Bundle> transportBundle() throws IOException {
    return new FhirGenerator<>(Bundle.class, "TransportBundleTemplate.json");
  }

  public static FhirGenerator<Parameters> gpasGetOrCreateResponse(
      Supplier<String> original, Supplier<String> pseudonym) throws IOException {
    var generator = new FhirGenerator<>(Parameters.class, "gpas-get-or-create-response.json");
    generator.replaceTemplateFieldWith("$ORIGINAL", original);
    generator.replaceTemplateFieldWith("$PSEUDONYM", pseudonym);
    return generator;
  }

  public void replaceTemplateFieldWith(String field, Supplier<String> replaceWith) {
    inputReplacements.put(field, replaceWith);
  }

  public Stream<T> generateResources() {
    return Stream.generate(this::generateResource);
  }

  /**
   * Generates the fhir resource using the template and parses it to resource
   *
   * @return
   */
  public T generateResource() {
    return FhirUtils.stringToFhirResource(resourceType, this.generateString());
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
