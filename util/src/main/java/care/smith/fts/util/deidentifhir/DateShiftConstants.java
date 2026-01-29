package care.smith.fts.util.deidentifhir;

/** Shared constants for date shifting functionality across CDA, TCA, and RDA modules. */
public interface DateShiftConstants {

  /** FHIR extension URL for storing transport IDs on date elements during deidentification. */
  String DATE_SHIFT_EXTENSION_URL =
      "https://fts.smith.care/fhir/StructureDefinition/date-shift-transport-id";

  /** Prefix used in Redis storage to distinguish date shift mappings from ID mappings. */
  String DATE_SHIFT_PREFIX = "ds:";
}
