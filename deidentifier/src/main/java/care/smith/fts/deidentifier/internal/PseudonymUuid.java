package care.smith.fts.deidentifier.internal;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Shapes a pseudonym into a UUID, so it can stand in a place that only accepts one. */
public interface PseudonymUuid {

  /** The literal form of a bundle-local resource name: {@code urn:uuid:<uuid>}. */
  String URN_UUID_PREFIX = "urn:uuid:";

  /** Whether the value names a resource in the literal urn form transaction bundles use. */
  static boolean isUrnUuid(String value) {
    return value.startsWith(URN_UUID_PREFIX);
  }

  /**
   * The value without a leading {@code urn:uuid:}, i.e. the bare uuid; any other value is returned
   * as it is.
   */
  static String stripUrnUuid(String value) {
    return isUrnUuid(value) ? value.substring(URN_UUID_PREFIX.length()) : value;
  }

  /**
   * The UUID a pseudonymized reference {@code Type/<pseudonym>} is carried as inside a {@code
   * urn:uuid:} value.
   *
   * <p>A {@code fullUrl} in literal urn form has to hold a valid URI, and {@code urn:uuid:}
   * promises an RFC 4122 UUID, which a pseudonym like {@code SYNTH-ID-7D2BAA5F} is not. This
   * function derives a name-based version 3 UUID from the UTF-8 bytes of the reference. The type is
   * part of the name, so two resources of different types stay apart even if their pseudonyms
   * coincide, as {@code Type/id} is unique in a bundle.
   *
   * <p>The mapping is deterministic and not reversible. It is applied to the pseudonymized
   * reference rather than to the raw value on purpose: everything that names one resource starts
   * from the same reference, so the values derived here agree and the links of the bundle resolve.
   *
   * <p>This is not a security function. Its input is already a pseudonym, so the hash needs no key
   * and hides nothing; it only puts the reference into UUID form.
   */
  static String uuidFrom(String reference) {
    return UUID.nameUUIDFromBytes(reference.getBytes(StandardCharsets.UTF_8)).toString();
  }
}
