package care.smith.fts.test;

import static care.smith.fts.util.FhirUtils.toBundle;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.FhirUtils;
import java.io.IOException;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

class FhirGeneratorTest {

  @Test
  void serializeAndDeserializeGenerateBundle() throws IOException {
    var gicsConsentGenerator =
        FhirGenerator.gicsResponse(() -> randomUUID().toString(), () -> randomUUID().toString());
    Bundle bundle = Stream.of(gicsConsentGenerator.generateResource()).collect(toBundle());

    assertThat(bundle).isNotNull();

    String serializedBundle = FhirUtils.fhirResourceToString(bundle);
    Bundle deserializedBundle = FhirUtils.stringToFhirBundle(serializedBundle);

    assertThat(deserializedBundle.equalsDeep(bundle)).isTrue();
  }
}
