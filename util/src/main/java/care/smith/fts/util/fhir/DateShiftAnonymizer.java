package care.smith.fts.util.fhir;

import static care.smith.fts.util.NanoIdUtils.nanoId;
import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_EXTENSION_URL;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.FhirTerser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses a FHIR Pseudonymizer anonymization config and selectively nullifies date elements matching
 * dateshift rules. For each nullified date, generates a transport ID and adds a DATE_SHIFT extension
 * so RDA can later restore the shifted date.
 */
public interface DateShiftAnonymizer {

  /**
   * Extracts dateshift FHIRPath-like rules from an anonymization.yaml config.
   *
   * <p>The expected format is:
   *
   * <pre>
   * fhirPathRules:
   *   - path: "Encounter.period.start"
   *     method: "dateshift"
   *   - path: "Encounter.period.end"
   *     method: "dateshift"
   *   - path: "Patient.identifier.value"
   *     method: "pseudonymize"
   * </pre>
   *
   * Only rules with {@code method: "dateshift"} are returned.
   *
   * @param configFile the anonymization.yaml file
   * @return list of dotted resource paths for dateshift rules (e.g., "Encounter.period.start")
   */
  @SuppressWarnings("unchecked")
  static List<String> parseDateShiftPaths(File configFile) throws IOException {
    var yaml = new Yaml();
    Map<String, Object> root;
    try (var input = new FileInputStream(configFile)) {
      root = yaml.load(input);
    }

    if (root == null || !root.containsKey("fhirPathRules")) {
      return List.of();
    }

    var rules = (List<Map<String, String>>) root.get("fhirPathRules");
    return rules.stream()
        .filter(rule -> "dateshift".equals(rule.get("method")))
        .map(rule -> rule.get("path"))
        .filter(path -> path != null && !path.isEmpty())
        .toList();
  }

  /**
   * Processes a bundle by nullifying date elements matching the given paths and adding dateshift
   * extensions with transport IDs.
   *
   * @param bundle the FHIR bundle to process (modified in place)
   * @param dateShiftPaths dotted resource paths (e.g., "Encounter.period.start")
   * @return map of tID → original date value
   */
  static Map<String, String> nullifyDates(Bundle bundle, List<String> dateShiftPaths) {
    if (dateShiftPaths.isEmpty()) {
      return Map.of();
    }

    var terser = new FhirTerser(FhirContext.forR4Cached());
    var dateMappings = new HashMap<String, String>();
    var dateValueToTid = new HashMap<String, String>();

    var pathsByResourceType =
        dateShiftPaths.stream()
            .filter(p -> p.contains("."))
            .collect(
                Collectors.groupingBy(
                    p -> p.substring(0, p.indexOf('.')),
                    Collectors.mapping(p -> p.substring(p.indexOf('.') + 1), Collectors.toList())));

    for (var entry : bundle.getEntry()) {
      var resource = entry.getResource();
      if (resource == null) continue;

      var resourceType = resource.fhirType();
      var paths = pathsByResourceType.get(resourceType);
      if (paths == null) continue;

      for (var subPath : paths) {
        processDateElements(terser, resource, subPath, dateMappings, dateValueToTid);
      }
    }

    return Map.copyOf(dateMappings);
  }

  private static void processDateElements(
      FhirTerser terser,
      Resource resource,
      String subPath,
      Map<String, String> dateMappings,
      Map<String, String> dateValueToTid) {

    var values = terser.getValues(resource, subPath);
    for (var value : values) {
      if (value instanceof BaseDateTimeType dateTime && dateTime.getValue() != null) {
        var originalDate = dateTime.getValueAsString();
        var tId = dateValueToTid.computeIfAbsent(originalDate, k -> nanoId());

        if (!dateMappings.containsKey(tId)) {
          dateMappings.put(tId, originalDate);
        }

        dateTime.setValue(null);
        dateTime.addExtension(DATE_SHIFT_EXTENSION_URL, new StringType(tId));
      }
    }
  }
}
