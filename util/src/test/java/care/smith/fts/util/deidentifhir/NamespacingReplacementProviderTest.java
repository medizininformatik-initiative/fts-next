package care.smith.fts.util.deidentifhir;

import static care.smith.fts.util.deidentifhir.NamespacingReplacementProvider.withNamespacing;
import static care.smith.fts.util.deidentifhir.NamespacingReplacementProvider.withoutNamespacing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class NamespacingReplacementProviderTest {
  @Test
  void missingNamespacedIdThrows() {
    var keyCreator = withNamespacing("pre");
    var provider = NamespacingReplacementProvider.of(keyCreator);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getIDReplacement("patient", "some"));
  }

  @Test
  void namespacedIdFound() {
    var keyCreator = withNamespacing("pre");
    var replacements = Map.of("pre.patient:some", "replacement");
    var provider = NamespacingReplacementProvider.of(keyCreator, replacements);

    assertThat(provider.getIDReplacement("patient", "some")).isEqualTo("replacement");
  }

  @Test
  void missingNamespacedValueThrows() {
    var keyCreator = withNamespacing("pre");
    var provider = NamespacingReplacementProvider.of(keyCreator);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getValueReplacement("patient", "some"));
  }

  @Test
  void namespacedValueFound() {
    var keyCreator = withNamespacing("pre");
    var replacements = Map.of("pre.identifier.system:some", "replacement");
    var provider = NamespacingReplacementProvider.of(keyCreator, replacements);

    assertThat(provider.getValueReplacement("system", "some")).isEqualTo("replacement");
  }

  @Test
  void missingIdThrows() {
    var keyCreator = withoutNamespacing();
    var provider = NamespacingReplacementProvider.of(keyCreator);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getIDReplacement("patient", "some"));
  }

  @Test
  void idFound() {
    var keyCreator = withoutNamespacing();
    var replacements = Map.of("some", "replacement");
    var provider = NamespacingReplacementProvider.of(keyCreator, replacements);

    assertThat(provider.getIDReplacement("system", "some")).isEqualTo("replacement");
  }

  @Test
  void missingValueThrows() {
    var keyCreator = withoutNamespacing();
    var provider = NamespacingReplacementProvider.of(keyCreator);

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> provider.getValueReplacement("patient", "some"));
  }

  @Test
  void valueFound() {
    var keyCreator = withoutNamespacing();
    var replacements = Map.of("some", "replacement");
    var provider = NamespacingReplacementProvider.of(keyCreator, replacements);

    assertThat(provider.getValueReplacement("system", "some")).isEqualTo("replacement");
  }
}
