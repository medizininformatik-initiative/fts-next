package care.smith.fts.test;

import static care.smith.fts.util.FhirUtils.toBundle;
import static java.lang.Math.min;

import care.smith.fts.util.FhirUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;

/**
 * The FhirGenerator reads a template file from disk and replaces given word e.g. $PATIENT_ID
 * occurrences with a given Replacement e.g. an UUID
 */
@Slf4j
@Getter
public class FhirGenerator {
  private final CharBuffer templateBuffer;
  private final Map<String, Replacement> inputReplacements = new HashMap<>();

  // This Map holds the replacements of the last run
  private final Map<String, List<String>> replacements = new HashMap<>();

  public FhirGenerator(String templateFile) throws IOException {
    templateBuffer = TemplateLoader.getCharBuffer(templateFile, this.getClass().getClassLoader());
  }

  public void replaceTemplateFieldWith(String field, Replacement replaceWith) {
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
    replacements.clear();
    return Stream.generate(this::generateString)
        .limit(min(pageSize, totalEntries))
        .map(FhirUtils::stringToFhirBundle)
        .collect(toBundle())
        .setTotal(totalEntries);
  }

  /**
   * Replaces the template fields
   *
   * @return The template file with replaced fields
   */
  public String generateString() {
    String s = templateBuffer.toString();
    for (Map.Entry<String, Replacement> m : inputReplacements.entrySet()) {
      var replacement = m.getValue().apply();
      addToReplacementMap(m, replacement);
      s = s.replace(m.getKey(), replacement);
    }
    return s;
  }

  private void addToReplacementMap(Entry<String, Replacement> m, String replacement) {
    var n = replacements.getOrDefault(m.getKey(), new ArrayList<>());
    n.add(replacement);
    replacements.put(m.getKey(), n);
  }

  public InputStream generateInputStream() {
    return new ByteArrayInputStream(generateString().getBytes());
  }

  public interface Replacement {
    String apply();
  }

  public static class UUID implements Replacement {
    @Override
    public String apply() {
      return java.util.UUID.randomUUID().toString();
    }
  }

  public static class Fixed implements Replacement {
    private final String value;

    public Fixed(String value) {
      this.value = value;
    }

    @Override
    public String apply() {
      return value;
    }
  }

  public static class OneOfList implements Replacement {
    private final List<String> values;
    private final Random random = new Random();

    public OneOfList(List<String> values) {
      this.values = values;
    }

    public OneOfList(List<String> values, long seed) {
      this.values = values;
      random.setSeed(seed);
    }

    @Override
    public String apply() {
      int nextInt = random.nextInt(values.size());
      return values.get(nextInt);
    }
  }

  public static class Incrementing implements Replacement {
    private final String prefix;
    private long index;

    public Incrementing(String prefix, long index) {
      this.prefix = prefix;
      this.index = index;
    }

    public Incrementing(String prefix) {
      this(prefix, 0);
    }

    @Override
    public String apply() {
      String s = prefix + index;
      index += 1;
      return s;
    }
  }
}
