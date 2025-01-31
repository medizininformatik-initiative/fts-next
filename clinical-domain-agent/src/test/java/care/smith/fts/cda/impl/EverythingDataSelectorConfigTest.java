package care.smith.fts.cda.impl;

import static care.smith.fts.cda.impl.EverythingDataSelectorConfig.DEFAULT_PAGE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import care.smith.fts.util.HttpClientConfig;
import org.junit.jupiter.api.Test;

class EverythingDataSelectorConfigTest {

  private static final HttpClientConfig FHIR_SERVER = new HttpClientConfig("http://localhost");

  @Test
  void fhirServerCannotBeNull() {
    assertThrows(
        NullPointerException.class, () -> new EverythingDataSelectorConfig(null, null, null));
  }

  @Test
  void fhirServerIsReturned() {
    assertThat(new EverythingDataSelectorConfig(FHIR_SERVER, null, null))
        .extracting(EverythingDataSelectorConfig::fhirServer)
        .isEqualTo(FHIR_SERVER);
  }

  @Test
  void emptyResolveIsValid() {
    assertThat(new EverythingDataSelectorConfig(FHIR_SERVER, null, null))
        .extracting(EverythingDataSelectorConfig::resolve)
        .isNull();
  }

  @Test
  void zeroPageSizeIsInvalid() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EverythingDataSelectorConfig(FHIR_SERVER, null, 0));
  }

  @Test
  void negativePageSizeIsInvalid() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new EverythingDataSelectorConfig(FHIR_SERVER, null, -1));
  }

  @Test
  void nullPageSizeUsesDefault() {
    assertThat(new EverythingDataSelectorConfig(FHIR_SERVER, null, null))
        .extracting(EverythingDataSelectorConfig::pageSize)
        .isEqualTo(DEFAULT_PAGE_SIZE);
  }

  @Test
  void positivePageSizeIsvalid() {
    final int somePageSize = 15;
    assertThat(new EverythingDataSelectorConfig(FHIR_SERVER, null, somePageSize))
        .extracting(EverythingDataSelectorConfig::pageSize)
        .isEqualTo(somePageSize);
  }
}
