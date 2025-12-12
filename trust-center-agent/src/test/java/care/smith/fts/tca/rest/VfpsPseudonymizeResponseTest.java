package care.smith.fts.tca.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.tca.rest.VfpsPseudonymizeResponse.PseudonymEntry;
import java.util.List;
import org.junit.jupiter.api.Test;

class VfpsPseudonymizeResponseTest {

  @Test
  void validResponseWithPseudonyms() {
    var entries =
        List.of(
            new PseudonymEntry("namespace", "original1", "pseudo1"),
            new PseudonymEntry("namespace", "original2", "pseudo2"));
    var response = new VfpsPseudonymizeResponse(entries);

    assertThat(response.pseudonyms()).hasSize(2);
    assertThat(response.pseudonyms().get(0).original()).isEqualTo("original1");
    assertThat(response.pseudonyms().get(1).pseudonym()).isEqualTo("pseudo2");
  }

  @Test
  void nullPseudonymsThrowsException() {
    assertThatThrownBy(() -> new VfpsPseudonymizeResponse(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("pseudonyms is required");
  }

  @Test
  void emptyPseudonymsListIsAllowed() {
    var response = new VfpsPseudonymizeResponse(List.of());

    assertThat(response.pseudonyms()).isEmpty();
  }

  @Test
  void pseudonymsListIsDefensivelyCopied() {
    var mutableList =
        new java.util.ArrayList<>(List.of(new PseudonymEntry("namespace", "original", "pseudo")));
    var response = new VfpsPseudonymizeResponse(mutableList);

    mutableList.add(new PseudonymEntry("namespace", "original2", "pseudo2"));

    assertThat(response.pseudonyms()).hasSize(1);
  }

  @Test
  void pseudonymEntryRecordStoresValues() {
    var entry = new PseudonymEntry("test-namespace", "original-value", "pseudonym-value");

    assertThat(entry.namespace()).isEqualTo("test-namespace");
    assertThat(entry.original()).isEqualTo("original-value");
    assertThat(entry.pseudonym()).isEqualTo("pseudonym-value");
  }
}
