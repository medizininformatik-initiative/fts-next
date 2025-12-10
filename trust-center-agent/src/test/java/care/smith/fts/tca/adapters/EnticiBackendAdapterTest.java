package care.smith.fts.tca.adapters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class EnticiBackendAdapterTest {

  private EnticiBackendAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new EnticiBackendAdapter();
  }

  @Test
  void getBackendTypeReturnsEntici() {
    assertThat(adapter.getBackendType()).isEqualTo("entici");
  }

  @Test
  void fetchOrCreatePseudonymThrowsUnsupportedOperationException() {
    var result = adapter.fetchOrCreatePseudonym("domain", "original");

    StepVerifier.create(result)
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UnsupportedOperationException.class);
              assertThat(error.getMessage())
                  .contains("Entici backend adapter is not yet implemented");
              assertThat(error.getMessage()).contains("gpas");
            })
        .verify();
  }

  @Test
  void fetchOrCreatePseudonymsThrowsUnsupportedOperationException() {
    var result = adapter.fetchOrCreatePseudonyms("domain", Set.of("original1", "original2"));

    StepVerifier.create(result)
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(UnsupportedOperationException.class);
              assertThat(error.getMessage())
                  .contains("Entici backend adapter is not yet implemented");
              assertThat(error.getMessage()).contains("gpas");
            })
        .verify();
  }
}
