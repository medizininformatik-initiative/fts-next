package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class VfpsPseudonymizeRequestTest {

  @Test
  void validRequestCreatesImmutableCopy() {
    var originals = List.of("original1", "original2");
    var request = new VfpsPseudonymizeRequest("namespace", originals);

    assertThat(request.namespace()).isEqualTo("namespace");
    assertThat(request.originals()).containsExactly("original1", "original2");
  }

  @Test
  void nullNamespaceThrowsNullPointerException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest(null, List.of("original")))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("namespace is required");
  }

  @Test
  void blankNamespaceThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("  ", List.of("original")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("namespace must not be blank");
  }

  @Test
  void emptyNamespaceThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("", List.of("original")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("namespace must not be blank");
  }

  @Test
  void nullOriginalsThrowsNullPointerException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("namespace", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("originals is required");
  }

  @Test
  void emptyOriginalsThrowsIllegalArgumentException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeRequest("namespace", List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("at least one original value required");
  }

  @Test
  void originalsListIsDefensivelyCopied() {
    var mutableList = new java.util.ArrayList<>(List.of("original1"));
    var request = new VfpsPseudonymizeRequest("namespace", mutableList);

    mutableList.add("original2");

    assertThat(request.originals()).containsExactly("original1");
  }
}
