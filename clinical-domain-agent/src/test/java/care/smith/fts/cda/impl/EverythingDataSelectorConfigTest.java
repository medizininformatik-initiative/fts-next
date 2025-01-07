package care.smith.fts.cda.impl;

import static org.junit.jupiter.api.Assertions.*;

import care.smith.fts.util.HttpClientConfig;
import org.junit.jupiter.api.Test;

class EverythingDataSelectorConfigTest {

  @Test
  void useDefaultIfPageSizeIsInvalid() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new EverythingDataSelectorConfig(new HttpClientConfig("http://localhost"), null, 0);
        });
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new EverythingDataSelectorConfig(new HttpClientConfig("http://localhost"), null, -1);
        });
  }
}
