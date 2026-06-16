package care.smith.fts.rda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FhirStoreBundleSenderTest {

  @Test
  void destinationIdReturnsPreComputedValue() {
    var sender = new FhirStoreBundleSender(null, null, "https://blaze.example/fhir");
    assertThat(sender.destinationId()).isEqualTo("https://blaze.example/fhir");
  }
}
