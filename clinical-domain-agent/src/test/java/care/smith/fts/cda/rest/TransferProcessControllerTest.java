package care.smith.fts.cda.rest;

import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.R4TransferProcessRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TransferProcessControllerTest {
  @Autowired TransferProcessController api;

  @Test
  void startExistingProjectSucceeds() {
    ConsentedPatient patient =
        new ConsentedPatient("patient-102931", new ConsentedPatient.ConsentedPolicies());
    create(api.start("example"))
        .expectNext(new R4TransferProcessRunner.Result(patient))
        .verifyComplete();
  }
}
