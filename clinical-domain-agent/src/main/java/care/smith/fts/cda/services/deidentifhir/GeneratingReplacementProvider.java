package care.smith.fts.cda.services.deidentifhir;

import static care.smith.fts.util.NanoIdUtils.nanoId;
import static care.smith.fts.util.deidentifhir.NamespacingReplacementProvider.withNamespacing;
import static java.util.Objects.requireNonNull;

import care.smith.fts.util.deidentifhir.NamespacingReplacementProvider.KeyCreator;
import de.ume.deidentifhir.util.IDReplacementProvider;
import de.ume.deidentifhir.util.IdentifierValueReplacementProvider;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates transport IDs (tIDs) on-the-fly during deidentification. Caches ID→tID mappings so the
 * same original ID always returns the same tID.
 *
 * <p>Implements both {@link IDReplacementProvider} for resource IDs and {@link
 * IdentifierValueReplacementProvider} for identifier values, generating tIDs using NanoId (21
 * chars).
 *
 * <p>This class is not thread-safe. Instances are confined to a single deidentification pass within
 * {@link care.smith.fts.cda.impl.DeidentifhirStep} and must not be shared across threads.
 */
public class GeneratingReplacementProvider
    implements IDReplacementProvider, IdentifierValueReplacementProvider {

  private final KeyCreator keyCreator;
  private final Map<String, String> idMappings = new HashMap<>();
  private final Map<String, String> dateMappings = new HashMap<>();
  private final Map<String, String> dateValueToTid = new HashMap<>();

  public GeneratingReplacementProvider(String patientIdentifier) {
    this.keyCreator = withNamespacing(requireNonNull(patientIdentifier));
  }

  @Override
  public String getIDReplacement(String resourceType, String id) {
    var key = keyCreator.getKeyForResourceTypeAndID(resourceType, id);
    return idMappings.computeIfAbsent(key, k -> nanoId());
  }

  @Override
  public String getValueReplacement(String system, String value) {
    var key = keyCreator.getKeyForSystemAndValue(system, value);
    return idMappings.computeIfAbsent(key, k -> nanoId());
  }

  /**
   * Generates a tID for a date value. Deduplicates so the same date value gets the same tID.
   *
   * @param dateValue the original date value (e.g., "2024-01-15")
   * @return the transport ID for this date
   */
  public String generateDateTid(String dateValue) {
    return dateValueToTid.computeIfAbsent(
        dateValue,
        v -> {
          var tId = nanoId();
          dateMappings.put(tId, dateValue);
          return tId;
        });
  }

  /**
   * Returns an immutable copy of all ID mappings (originalID→tID).
   *
   * @return map of namespaced original IDs to their transport IDs
   */
  public Map<String, String> getIdMappings() {
    return Map.copyOf(idMappings);
  }

  /**
   * Returns an immutable copy of all date mappings (tID→originalDate).
   *
   * @return map of date transport IDs to their original date values
   */
  public Map<String, String> getDateMappings() {
    return Map.copyOf(dateMappings);
  }
}
