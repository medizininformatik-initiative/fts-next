package care.smith.fts.tca.adapters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.deidentification.EnticiClient;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class EnticiBackendAdapterTest {

  @Mock private EnticiClient enticiClient;

  private EnticiBackendAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new EnticiBackendAdapter(enticiClient);
  }

  @Test
  void getBackendTypeReturnsEntici() {
    assertThat(adapter.getBackendType()).isEqualTo("entici");
  }

  @Test
  void fetchOrCreatePseudonymDelegatesToEnticiClient() {
    var domain = "test-domain";
    var originalValue = "patient-123";
    var expectedPseudonym = "pseudo-456";

    when(enticiClient.fetchOrCreatePseudonym(domain, originalValue))
        .thenReturn(Mono.just(expectedPseudonym));

    var result = adapter.fetchOrCreatePseudonym(domain, originalValue);

    StepVerifier.create(result).expectNext(expectedPseudonym).verifyComplete();
  }

  @Test
  void fetchOrCreatePseudonymsDelegatesToEnticiClient() {
    var domain = "test-domain";
    var originals = Set.of("patient-1", "patient-2");
    var expected = Map.of("patient-1", "pseudo-1", "patient-2", "pseudo-2");

    when(enticiClient.fetchOrCreatePseudonyms(domain, originals)).thenReturn(Mono.just(expected));

    var result = adapter.fetchOrCreatePseudonyms(domain, originals);

    StepVerifier.create(result)
        .assertNext(
            mappings -> {
              assertThat(mappings).hasSize(2);
              assertThat(mappings).containsEntry("patient-1", "pseudo-1");
              assertThat(mappings).containsEntry("patient-2", "pseudo-2");
            })
        .verifyComplete();
  }

  @Test
  void fetchOrCreatePseudonymsHandlesEmptySet() {
    var domain = "test-domain";
    var originals = Set.<String>of();

    when(enticiClient.fetchOrCreatePseudonyms(domain, originals)).thenReturn(Mono.just(Map.of()));

    var result = adapter.fetchOrCreatePseudonyms(domain, originals);

    StepVerifier.create(result)
        .assertNext(mappings -> assertThat(mappings).isEmpty())
        .verifyComplete();
  }
}
