package care.smith.fts.test;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.FhirUtils;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;

class FhirGeneratorTest {

  @Test
  void serializeAndDeserializeGenerateBundle() throws IOException {
    FhirGenerator gicsConsentGenerator =
        FhirGenerator.gicsResponse(() -> randomUUID().toString(), () -> randomUUID().toString());
    Bundle bundle = gicsConsentGenerator.generateBundle(1, 200);

    assertThat(bundle).isNotNull();

    String serializedBundle = FhirUtils.fhirResourceToString(bundle);
    Bundle deserializedBundle = FhirUtils.stringToFhirBundle(serializedBundle);

    assertThat(deserializedBundle.equalsDeep(bundle)).isTrue();
  }
}
