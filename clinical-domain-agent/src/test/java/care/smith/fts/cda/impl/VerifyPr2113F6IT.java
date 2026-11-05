package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Reproduces finding F6 of the review of PR #2113. */
@SpringBootTest
class VerifyPr2113F6IT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientFactory clientFactory;

  /** F6: a project with {@code policies: []} must fail to load. */
  @Test
  void f6EmptyPoliciesAreRejected() {
    var factory =
        new TcaCohortSelectorFactory(clientFactory, new DefaultRetryStrategy(meterRegistry));
    var config =
        new TcaCohortSelectorConfig(
            new HttpClientConfig("http://tca"), "pid-system", "policy-system", Set.of(), "d", null);

    assertThatThrownBy(() -> factory.create(null, config)).isInstanceOf(RuntimeException.class);
  }
}
