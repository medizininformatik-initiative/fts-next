package care.smith.fts.deidentifier.internal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Shapes a pseudonym into a UUID, so it can stand in a place that only accepts one. */
public interface PseudonymUuid {

  /** The offset basis of the 128-bit FNV-1a hash, as its high and low 64 bits. */
  long OFFSET_BASIS_HIGH = 0x6c62272e07bb0142L;

  long OFFSET_BASIS_LOW = 0x62b821756295c58dL;

  /** The 128-bit FNV prime {@code 2^88 + 0x13b}, as its high and low 64 bits. */
  long PRIME_HIGH = 0x0000000001000000L;

  long PRIME_LOW = 0x000000000000013bL;

  String VARIANT_NIBBLES = "89ab";

  /** The literal form of a bundle-local resource name: {@code urn:uuid:<uuid>}. */
  String URN_UUID_PREFIX = "urn:uuid:";

  /** Whether the value names a resource in the literal urn form transaction bundles use. */
  static boolean isUrnUuid(String value) {
    return value.startsWith(URN_UUID_PREFIX);
  }

  /** The bare uuid of a {@code urn:uuid:} value, or the value itself when it carries no prefix. */
  static String stripUrnUuid(String value) {
    return isUrnUuid(value) ? value.substring(URN_UUID_PREFIX.length()) : value;
  }

  /**
   * The UUID a pseudonym is carried as inside a {@code urn:uuid:} value.
   *
   * <p>A reference and a {@code fullUrl} in literal urn form have to hold a valid URI, and {@code
   * urn:uuid:} promises an RFC 4122 UUID, which a pseudonym like {@code SYNTH-ID-7D2BAA5F} is not:
   * the HL7 validator rejects it. This function derives one from the pseudonym instead: the 128-bit
   * FNV-1a hash of the pseudonym, with the version nibble stamped to {@code 4} and the variant
   * nibble taken from the two lowest hash bits, so the result reads as a normal random UUID.
   *
   * <p>The mapping is deterministic and collision-resistant, not reversible, and it is applied to
   * the pseudonym rather than to the raw value on purpose: everything that names one resource — its
   * id, the {@code fullUrl} of its entry, and every reference to it — starts from the same
   * pseudonym, so the values derived here agree and the links of the bundle resolve. The resource
   * keeps the plain pseudonym as its id, which {@code Resource.id} accepts and this UUID form does
   * not improve on.
   */
  static String uuidFrom(String pseudonym) {
    long[] hash = fnv1a128(pseudonym.getBytes(StandardCharsets.UTF_8));
    char[] hex = String.format(Locale.ROOT, "%016x%016x", hash[0], hash[1]).toCharArray();
    hex[12] = '4';
    hex[16] = VARIANT_NIBBLES.charAt((int) (hash[1] & 3));
    String digits = new String(hex);
    return "%s-%s-%s-%s-%s"
        .formatted(
            digits.substring(0, 8),
            digits.substring(8, 12),
            digits.substring(12, 16),
            digits.substring(16, 20),
            digits.substring(20));
  }

  /**
   * The 128-bit FNV-1a hash as {@code {high, low}}. The loop stays imperative on purpose: it is the
   * textbook form of the algorithm, carries two coupled accumulators per byte, and has no
   * collection to stream over.
   */
  private static long[] fnv1a128(byte[] bytes) {
    long high = OFFSET_BASIS_HIGH;
    long low = OFFSET_BASIS_LOW;
    for (byte b : bytes) {
      low ^= b & 0xFFL;
      // (high:low) * (PRIME_HIGH: PRIME_LOW) truncated to 128 bits
      long productHigh =
          Math.unsignedMultiplyHigh(low, PRIME_LOW) + low * PRIME_HIGH + high * PRIME_LOW;
      low = low * PRIME_LOW;
      high = productHigh;
    }
    return new long[] {high, low};
  }
}
