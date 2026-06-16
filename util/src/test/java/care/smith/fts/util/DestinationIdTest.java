package care.smith.fts.util;

import static care.smith.fts.util.DestinationId.fromBaseUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DestinationIdTest {

  @Test
  void stripsTrailingSlash() {
    assertThat(fromBaseUrl("https://blaze.example/fhir/")).isEqualTo("blaze.example/fhir");
  }

  @Test
  void stripsMultipleTrailingSlashes() {
    assertThat(fromBaseUrl("https://blaze.example/fhir///")).isEqualTo("blaze.example/fhir");
  }

  @Test
  void lowercasesScheme() {
    assertThat(fromBaseUrl("HTTPS://blaze.example/fhir")).isEqualTo("blaze.example/fhir");
  }

  @Test
  void lowercasesHost() {
    assertThat(fromBaseUrl("https://Blaze.Example.COM/fhir")).isEqualTo("blaze.example.com/fhir");
  }

  @Test
  void preservesPathCase() {
    assertThat(fromBaseUrl("https://blaze.example/FHIR")).isEqualTo("blaze.example/FHIR");
  }

  @Test
  void pathCaseMakesIdsDifferent() {
    assertThat(fromBaseUrl("https://blaze.example/FHIR"))
        .isNotEqualTo(fromBaseUrl("blaze.example/fhir"));
  }

  @Test
  void equivalentUrlsYieldSameId() {
    assertThat(fromBaseUrl("https://Blaze.example/FHIR/"))
        .isEqualTo(fromBaseUrl("blaze.example/FHIR"));
  }

  @Test
  void preservesPort() {
    assertThat(fromBaseUrl("https://blaze.example:8080/fhir")).isEqualTo("blaze.example:8080/fhir");
  }

  @Test
  void preservesMultiSegmentPath() {
    assertThat(fromBaseUrl("https://blaze.example:8080/ttp-fhir/fhir/gpas"))
        .isEqualTo("blaze.example:8080/ttp-fhir/fhir/gpas");
  }

  @Test
  void noSchemeReturnedTrimmed() {
    assertThat(fromBaseUrl("  /only/path/  ")).isEqualTo("/only/path/");
  }

  @Test
  void noPathReturnedWithoutTrailingSlash() {
    assertThat(fromBaseUrl("https://blaze.example")).isEqualTo("blaze.example");
  }

  @Test
  void stripsWhitespace() {
    assertThat(fromBaseUrl("  https://blaze.example/fhir  ")).isEqualTo("blaze.example/fhir");
  }

  @Test
  void rejectsNull() {
    assertThatThrownBy(() -> fromBaseUrl(null)).isInstanceOf(NullPointerException.class);
  }
}
