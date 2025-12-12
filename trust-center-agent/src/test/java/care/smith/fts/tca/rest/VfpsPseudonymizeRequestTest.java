package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class VfpsPseudonymizeRequestTest {

  @Test
  void validRequestCreatesImmutableCopy() {
    var originals = List.of("original1", "original2");
    var request = new VfpsPseudonymizeRequest("namespace", originals, "transfer-123");

    assertThat(request.namespace()).isEqualTo("namespace");
    assertThat(request.originals()).containsExactly("original1", "original2");
    assertThat(request.transferId()).isEqualTo("transfer-123");
  }

  @Test
  void nullNamespaceThrowsNullPointerException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest(null, List.of("original"), "transfer-123"))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("namespace is required");
  }

  @Test
  void blankNamespaceThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("  ", List.of("original"), "transfer-123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("namespace must not be blank");
  }

  @Test
  void emptyNamespaceThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("", List.of("original"), "transfer-123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("namespace must not be blank");
  }

  @Test
  void nullOriginalsThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("namespace", null, "transfer-123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("at least one original value required");
  }

  @Test
  void emptyOriginalsThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("namespace", List.of(), "transfer-123"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("at least one original value required");
  }

  @Test
  void nullTransferIdThrowsNullPointerException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("namespace", List.of("original"), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("transferId is required");
  }

  @Test
  void originalsListIsDefensivelyCopied() {
    var mutableList = new java.util.ArrayList<>(List.of("original1"));
    var request = new VfpsPseudonymizeRequest("namespace", mutableList, "transfer-123");

    mutableList.add("original2");

    assertThat(request.originals()).containsExactly("original1");
  }
}
