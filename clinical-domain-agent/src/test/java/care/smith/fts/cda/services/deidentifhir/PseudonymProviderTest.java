package care.smith.fts.cda.services.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import care.smith.fts.util.tca.TransportIDs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PseudonymProviderTest {
  PseudonymProvider provider;

  @BeforeEach
  void setUp() {
    TransportIDs transportIDs = new TransportIDs();
    transportIDs.put("test.id.Patient:id1", "tid1");
    transportIDs.put("test.identifier.Patient:id1", "tid1");
    provider = new PseudonymProvider(new NamespacingService("test"), transportIDs);
  }

  @Test
  void getIDReplacement() {
    assertThat(provider.getIDReplacement("Patient", "id1")).isEqualTo("tid1");
  }

  @Test
  void getValueReplacement() {
    assertThat(provider.getValueReplacement("Patient", "id1")).isEqualTo("tid1");
  }
}
