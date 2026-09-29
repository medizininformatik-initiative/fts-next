package care.smith.fts.tca;

import static care.smith.fts.tca.TtpFhirGatewayUtil.handleError;
import static reactor.core.Exceptions.retryExhausted;
import static reactor.test.StepVerifier.create;

import org.junit.jupiter.api.Test;

class TtpFhirGatewayUtilTest {

  @Test
  void retryExhaustedWithOtherCausePassesThrough() {
    var error = retryExhausted("exhausted", new IllegalStateException("cause-111204"));

    create(handleError("gPAS", error)).expectErrorMatches(e -> e == error).verify();
  }
}
