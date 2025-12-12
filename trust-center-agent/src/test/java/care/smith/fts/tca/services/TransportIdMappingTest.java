package care.smith.fts.tca.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransportIdMappingTest {

  @Test
  void createValidMapping() {
    var mapping = new TransportIdMapping("tId-123", "sId-456", "domain", "transfer-1");

    assertThat(mapping.transportId()).isEqualTo("tId-123");
    assertThat(mapping.securePseudonym()).isEqualTo("sId-456");
    assertThat(mapping.domain()).isEqualTo("domain");
    assertThat(mapping.transferId()).isEqualTo("transfer-1");
  }

  @Test
  void nullTransportIdThrowsException() {
    assertThatThrownBy(() -> new TransportIdMapping(null, "sId", "domain", "transfer"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("transportId is required");
  }

  @Test
  void nullSecurePseudonymThrowsException() {
    assertThatThrownBy(() -> new TransportIdMapping("tId", null, "domain", "transfer"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("securePseudonym is required");
  }

  @Test
  void nullDomainThrowsException() {
    assertThatThrownBy(() -> new TransportIdMapping("tId", "sId", null, "transfer"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("domain is required");
  }

  @Test
  void nullTransferIdThrowsException() {
    assertThatThrownBy(() -> new TransportIdMapping("tId", "sId", "domain", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("transferId is required");
  }
}
