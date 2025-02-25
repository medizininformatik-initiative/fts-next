package care.smith.fts.tca.consent.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoGicsExceptionTest {

  @Test
  void shouldCreateExceptionWithDefaultConstructor() {
    assertThat(new NoGicsException()).isNotNull().isInstanceOf(Exception.class);
  }
}
