package care.smith.fts.util;

import static care.smith.fts.util.NanoIdUtils.nanoId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NanoIdUtilsTest {
  private static Stream<Arguments> significantSizes() {
    return IntStream.rangeClosed(1, 21).mapToObj(Arguments::of);
  }

  @ParameterizedTest(name = "size of {0}")
  @MethodSource("significantSizes")
  public void idHasSpecifiedSize(int size) {
    assertThat(nanoId(size)).hasSize(size);
  }

  @Test
  public void zeroSizeNotAllowed() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> nanoId(0));
  }

  @Test
  public void negativeSizeNotAllowed() {
    assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> nanoId(-1));
  }
}
