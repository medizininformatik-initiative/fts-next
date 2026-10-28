package care.smith.fts.deidentifier.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PseudonymUuidTest {

  @Test
  void isDeterministic() {
    assertThat(PseudonymUuid.uuidFrom("Patient/SYNTH-ID-1"))
        .isEqualTo(PseudonymUuid.uuidFrom("Patient/SYNTH-ID-1"));
  }

  @Test
  void differentNamesGiveDifferentUuids() {
    assertThat(PseudonymUuid.uuidFrom("Patient/SYNTH-ID-1"))
        .isNotEqualTo(PseudonymUuid.uuidFrom("Patient/SYNTH-ID-2"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a", "Patient/SYNTH-ID-7D2BAA5F", "Ünïcode"})
  void givesANameBasedVersion3Uuid(String name) {
    var uuid = UUID.fromString(PseudonymUuid.uuidFrom(name));

    assertThat(uuid.version()).isEqualTo(3);
    assertThat(uuid.variant()).isEqualTo(2);
  }

  @Test
  void hashesTheEmptyNameToTheMd5OfNothingWithStampedNibbles() {
    // MD5("") = d41d8cd98f00b204e9800998ecf8427e; version 3 gives -3204-, variant 10 gives -a980-
    assertThat(PseudonymUuid.uuidFrom("")).isEqualTo("d41d8cd9-8f00-3204-a980-0998ecf8427e");
  }

  @Test
  void recognisesUrnUuid() {
    assertThat(PseudonymUuid.URN_UUID_PREFIX).isEqualTo("urn:uuid:");
    assertThat(PseudonymUuid.isUrnUuid("urn:uuid:1234")).isTrue();
    assertThat(PseudonymUuid.isUrnUuid("urn:oid:1.2")).isFalse();
    assertThat(PseudonymUuid.isUrnUuid("Patient/1")).isFalse();
  }
}
