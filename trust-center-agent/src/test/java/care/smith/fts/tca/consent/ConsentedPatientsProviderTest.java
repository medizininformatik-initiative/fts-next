package care.smith.fts.tca.consent;

import static org.junit.Assert.assertThrows;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import org.junit.jupiter.api.Test;

public class ConsentedPatientsProviderTest {

  @Test
  void assertInvalidPagingArgsThrow() {
    assertThrows(IllegalArgumentException.class, () -> new PagingParams(-1, 1));
    assertThrows(IllegalArgumentException.class, () -> new PagingParams(1, -1));
    assertThrows(IllegalArgumentException.class, () -> new PagingParams(-1, -1));
    assertThrows(
        IllegalArgumentException.class, () -> new PagingParams(Integer.MAX_VALUE - 20, 25));
  }
}
