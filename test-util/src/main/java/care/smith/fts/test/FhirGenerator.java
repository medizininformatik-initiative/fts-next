package care.smith.fts.test;

import static java.util.Objects.requireNonNull;

import care.smith.fts.util.fhir.FhirUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Resource;

/**
 * The FhirGenerator reads a template file from disk and replaces given word e.g. $PATIENT_ID
 * occurrences with a given Replacement e.g. an UUID
 */
@Slf4j
public class FhirGenerator<T extends Resource> {
  private final Class<T> resourceType;
  private final CharBuffer templateBuffer;
  private final Map<String, Supplier<String>> inputReplacements;

  FhirGenerator(
      Class<T> resourceType, String templateFile, Map<String, Supplier<String>> inputReplacements)
      throws IOException {
    this.templateBuffer = readTemplate(templateFile);
    this.resourceType = resourceType;
    this.inputReplacements = inputReplacements;
  }

  private static CharBuffer readTemplate(String templateFile) throws IOException {
    try (InputStream g = FhirGenerator.class.getResourceAsStream(templateFile)) {
      requireNonNull(g, "Cannot find template '" + templateFile + "'");
      return StandardCharsets.UTF_8.decode(ByteBuffer.wrap(g.readAllBytes()));
    }
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
}
