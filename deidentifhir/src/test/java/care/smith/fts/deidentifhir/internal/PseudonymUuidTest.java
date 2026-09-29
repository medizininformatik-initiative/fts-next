package care.smith.fts.deidentifhir.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PseudonymUuidTest {

  /** The javadoc claims determinism; this pins it, together with the RFC 4122 shape. */
  @Test
  void uuidFromIsDeterministicAndShapedLikeARandomUuid() {
    String first = PseudonymUuid.uuidFrom("SYNTH-ID-7D2BAA5F");
    String again = PseudonymUuid.uuidFrom("SYNTH-ID-7D2BAA5F");
    String other = PseudonymUuid.uuidFrom("SYNTH-ID-7D2BAA60");

    assertThat(again).isEqualTo(first);
    assertThat(other).isNotEqualTo(first);
    assertThat(first)
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  }

  /**
   * Pins the shaping itself: the UUID is the 128-bit FNV-1a hash of the pseudonym with the version
   * nibble stamped to {@code 4} and the variant nibble taken from the two lowest hash bits. The
   * expected values were produced by an independent implementation of that algorithm.
   */
  @Test
  void uuidFromIsTheShapedFnvHashOfThePseudonym() {
    assertThat(PseudonymUuid.uuidFrom("SYNTH-1")).isEqualTo("79224f4a-b34f-478d-be55-50c9cf9d4f37");
    assertThat(PseudonymUuid.uuidFrom("id-pseudonym"))
        .isEqualTo("52a47402-9e1f-438b-b31c-fad31f14ca93");
  }

  @Test
  void stripUrnUuidRemovesOnlyThePrefix() {
    assertThat(PseudonymUuid.stripUrnUuid("urn:uuid:8d1f-42")).isEqualTo("8d1f-42");
    assertThat(PseudonymUuid.stripUrnUuid("Patient/123")).isEqualTo("Patient/123");
  }
}
